import { ChangeDetectionStrategy, Component, computed, effect, ElementRef, inject, ViewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { GameStore } from '../core/game-store.service';
import { Preferences } from '../core/preferences.service';
import { Color, PieceType, PIECE_NAMES, TERMINATIONS, DIFFICULTIES, GameSettings } from '../core/models';
import { GameSetupComponent } from './game-setup.component';
import { BoardComponent } from '../shared/board.component';
import { PieceComponent } from '../shared/piece.component';

@Component({
  selector: 'app-play',
  imports: [GameSetupComponent, RouterLink, BoardComponent, PieceComponent],
  templateUrl: './play.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PlayComponent {
  readonly store = inject(GameStore);
  readonly preferences = inject(Preferences);
  readonly terminations = TERMINATIONS;
  readonly pieceNames = PIECE_NAMES;
  readonly promotionTypes: PieceType[] = ['Q', 'R', 'B', 'N'];
  readonly difficulties = DIFFICULTIES;
  action = '';
  @ViewChild('newDialog') newDialog?: ElementRef<HTMLDialogElement>;
  @ViewChild('confirmDialog') confirmDialog?: ElementRef<HTMLDialogElement>;
  @ViewChild('promotionDialog') promotionDialog?: ElementRef<HTMLDialogElement>;
  readonly rows = computed(() => {
    const moves = this.store.game()?.moves ?? [];
    return Array.from({ length: Math.ceil(moves.length / 2) }, (_, i) => ({ number: i + 1, white: moves[i * 2], black: moves[i * 2 + 1] }));
  });
  constructor() {
    effect(() => {
      if (this.store.promotion()) this.promotionDialog?.nativeElement.showModal();
      else this.promotionDialog?.nativeElement.close();
    });
  }
  player(color: Color): string { return color === 'WHITE' ? (this.store.game()?.whiteName ?? 'Jogador 1') : (this.store.game()?.blackName ?? 'Jogador 2'); }
  topColor(): Color { return this.store.flipped() ? 'WHITE' : 'BLACK'; }
  bottomColor(): Color { return this.store.flipped() ? 'BLACK' : 'WHITE'; }
  capturedBy(color: Color) { return this.store.game()?.captured.filter(piece => piece.color !== color) ?? []; }
  async start(settings: GameSettings, dialog = false) {
    if (await this.store.create(settings)) { if (dialog) this.newDialog?.nativeElement.close(); }
  }
  openNew() {
    this.newDialog?.nativeElement.showModal();
  }
  confirm(action: string) { this.action = action; this.confirmDialog?.nativeElement.showModal(); }
  async finish() { if (await this.store.finish(this.action)) this.confirmDialog?.nativeElement.close(); }
  async drop(move: { from: string; to: string }) {
    this.store.clearSelection(); await this.store.select(move.from); await this.store.moveTo(move.to);
  }
  navigate(delta: number) {
    const count = this.store.game()?.moves.length ?? 0;
    const next = Math.max(0, Math.min(count, (this.store.reviewPly() ?? count) + delta));
    this.store.review(next === count ? null : next);
  }
}
