import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { GameApi } from './game-api.service';
import { EngineStatus, Game, GamePage, GameSettings, INITIAL_FEN, TERMINATIONS } from './models';
import { Preferences, readStorage, writeStorage } from './preferences.service';

@Injectable({ providedIn: 'root' })
export class GameStore {
  private readonly api = inject(GameApi);
  private readonly preferences = inject(Preferences);
  readonly game = signal<Game | null>(null);
  readonly history = signal<GamePage>({ items: [], page: 0, totalPages: 0, totalItems: 0 });
  readonly busy = signal(false);
  readonly thinking = signal(false);
  readonly engineStatus = signal<EngineStatus | null>(null);
  readonly computerTurn = computed(() => {
    const game = this.game();
    return !!game && game.mode === 'COMPUTER' && game.result === '*' && game.currentPlayer !== game.humanColor;
  });
  readonly listing = signal(false);
  readonly ready = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly selected = signal<string | null>(null);
  readonly legal = signal<string[]>([]);
  readonly selecting = signal(false);
  readonly flipped = signal(false);
  readonly reviewPly = signal<number | null>(null);
  readonly promotion = signal<{ from: string; to: string } | null>(null);
  readonly reviewing = computed(() => this.reviewPly() !== null && this.reviewPly() !== this.game()?.moves.length);
  readonly displayedFen = computed(() => {
    const game = this.game(), ply = this.reviewPly();
    if (!game) return INITIAL_FEN;
    return ply === null ? game.fen : ply === 0 ? game.initialFen : (game.moves[ply - 1]?.fen ?? game.fen);
  });
  readonly lastMove = computed(() => {
    const game = this.game();
    return game?.moves[(this.reviewPly() ?? game.moves.length) - 1] ?? null;
  });
  readonly status = computed(() => {
    const game = this.game();
    if (!game) return 'O próximo lance é seu.';
    if (this.reviewing()) return `Revisando lance ${this.reviewPly()}. Volte à posição atual para jogar.`;
    if (game.result !== '*') {
      const winner = game.result === '1-0' ? game.whiteName : game.blackName;
      return game.result === '1/2-1/2' ? TERMINATIONS[game.termination] : `${winner} venceu · ${TERMINATIONS[game.termination]}`;
    }
    if (this.computerTurn()) return this.thinking() ? 'Stockfish está pensando…' : 'Aguardando o computador';
    return `${game.check ? 'Xeque! ' : ''}Vez de ${game.currentPlayer === 'WHITE' ? game.whiteName : game.blackName}`;
  });
  private selectionRequest = 0;

  constructor() { void this.initialize(); }
  private async initialize() {
    await Promise.all([this.loadHistory(), this.loadEngineStatus()]);
    const last = readStorage('chess-last-game');
    if (last && this.history().items.some(game => game.id === last)) await this.load(last);
    this.ready.set(true);
  }
  async loadHistory(page = 0) {
    this.listing.set(true);
    try { this.history.set(await this.api.list(page)); }
    catch (error) { this.report(error); }
    finally { this.listing.set(false); }
  }
  async loadEngineStatus() {
    try { this.engineStatus.set(await this.api.engineStatus()); }
    catch { this.engineStatus.set({ available: false, name: 'Stockfish', message: 'Não foi possível consultar o motor. Confira o backend.' }); }
  }
  async create(settings: GameSettings): Promise<boolean> {
    const created = await this.operation(async () => {
      this.accept(await this.api.create(settings));
      this.flipped.set(settings.mode === 'COMPUTER' && settings.humanColor === 'BLACK');
      this.notice.set('Partida criada. Boa partida!');
      void this.loadHistory();
    });
    if (created) void this.playComputer();
    return created;
  }
  async load(id: string): Promise<boolean> {
    const loaded = await this.operation(async () => {
      const game = await this.api.get(id); this.accept(game);
      this.flipped.set(game.mode === 'COMPUTER' && game.humanColor === 'BLACK');
      this.notice.set('Partida carregada.');
    });
    if (loaded) void this.playComputer();
    return loaded;
  }
  private accept(game: Game) {
    this.game.set(game); writeStorage('chess-last-game', game.id);
    this.reviewPly.set(null); this.clearSelection(); this.promotion.set(null);
  }
  clearSelection() {
    this.selectionRequest++; this.selected.set(null); this.legal.set([]); this.selecting.set(false);
  }
  async select(square: string) {
    const game = this.game();
    if (!game || this.busy() || this.computerTurn() || this.reviewing() || game.result !== '*') return;
    if (this.legal().includes(square) && this.selected()) { await this.moveTo(square); return; }
    if (square === this.selected()) { this.clearSelection(); return; }
    const piece = game.board.find(p => p.square === square);
    if (!piece || piece.color !== game.currentPlayer) { this.clearSelection(); return; }
    this.clearSelection(); this.selected.set(square); this.selecting.set(true); this.error.set('');
    const request = this.selectionRequest;
    try {
      const moves = await this.api.legal(game.id, square);
      if (request === this.selectionRequest) this.legal.set(moves);
    } catch (error) { if (request === this.selectionRequest) this.report(error); }
    finally { if (request === this.selectionRequest) this.selecting.set(false); }
  }
  async moveTo(to: string) {
    const game = this.game(), from = this.selected();
    if (!game || !from || !this.legal().includes(to) || this.busy() || this.reviewing()) return;
    const piece = game.board.find(p => p.square === from);
    if (piece?.type === 'P' && (to.endsWith('1') || to.endsWith('8'))) {
      this.promotion.set({ from, to }); return;
    }
    await this.submitMove(from, to, 'Q');
  }
  async promote(type: string) {
    const pending = this.promotion();
    if (pending) await this.submitMove(pending.from, pending.to, type);
  }
  private async submitMove(from: string, to: string, promotion: string) {
    const game = this.game();
    if (!game) return;
    const moved = await this.operation(async () => {
      this.accept(await this.api.move(game, from, to, promotion));
      this.notice.set('Jogada salva.'); void this.preferences.playMove();
    }, true);
    if (moved) await this.playComputer();
  }
  async playComputer() {
    const game = this.game();
    if (!game || !this.computerTurn() || this.busy() || this.reviewing()) return;
    this.clearSelection(); this.thinking.set(true);
    try {
      await this.operation(async () => {
        this.accept(await this.api.computerMove(game));
        this.notice.set('O computador jogou. Sua vez!'); void this.preferences.playMove();
      }, true);
    } finally { this.thinking.set(false); }
  }
  async finish(action: string): Promise<boolean> {
    const game = this.game();
    if (!game || this.reviewing()) return false;
    return this.operation(async () => {
      this.accept(await this.api.finish(game, action)); void this.loadHistory();
    }, true);
  }
  review(ply: number | null) {
    this.clearSelection(); this.reviewPly.set(ply); this.promotion.set(null);
  }
  async exportPgn() {
    const game = this.game(); if (!game) return;
    await this.operation(async () => {
      const data = await this.api.pgn(game.id);
      this.download(data, `chess-${game.id}.pgn`, 'application/x-chess-pgn');
      this.notice.set('PGN exportado.');
    });
  }
  exportFen() {
    this.download(this.displayedFen(), 'posicao.fen', 'text/plain'); this.notice.set('Posição FEN exportada.');
  }
  private download(data: string, name: string, type: string) {
    const url = URL.createObjectURL(new Blob([data], { type }));
    const link = document.createElement('a'); link.href = url; link.download = name; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  private async operation(action: () => Promise<void>, reconcile = false): Promise<boolean> {
    if (this.busy()) return false;
    this.busy.set(true); this.error.set(''); this.notice.set('');
    try { await action(); return true; }
    catch (error) {
      this.report(error);
      if (reconcile && this.game()) {
        try { this.accept(await this.api.get(this.game()!.id)); }
        catch { this.notice.set('Use Atualizar partida para conferir se a última ação foi salva.'); }
      }
      return false;
    } finally { this.busy.set(false); }
  }
  private report(error: unknown) {
    this.error.set(error instanceof HttpErrorResponse && typeof error.error?.detail === 'string'
      ? error.error.detail : 'Não foi possível conectar ao servidor. Confira se o backend está em execução e tente novamente.');
  }
}
