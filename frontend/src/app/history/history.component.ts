import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { GameStore } from '../core/game-store.service';
import { DIFFICULTIES, TERMINATIONS } from '../core/models';

@Component({
  selector: 'app-history',
  imports: [DatePipe, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page-heading"><div><span class="eyebrow">CADA PARTIDA, UMA HISTÓRIA</span><h1>Seus lances ficam.</h1><p>Retome uma partida ou revisite o que aprendeu.</p></div><a class="button primary" routerLink="/play">Voltar ao tabuleiro →</a></section>
    <section class="history-section"><div class="section-heading"><h2>Minhas partidas <span class="count-badge">{{ store.history().totalItems }}</span></h2><button class="button secondary" type="button" [disabled]="store.listing()" (click)="store.loadHistory(store.history().page)">↻ Atualizar</button></div>
      @if (store.listing()) { <p class="empty-state" role="status">Carregando partidas…</p> }
      @else if (store.history().items.length === 0) { <div class="empty-state"><span class="empty-symbol">◷</span><h2>Sua história começa no tabuleiro.</h2><p>As partidas deste navegador aparecem aqui, salvas lance a lance.</p><a class="button primary" routerLink="/play">Começar a jogar →</a></div> }
      @else { <div class="history-grid">@for (game of store.history().items; track game.id) {
        <article class="history-card"><div class="history-card-top"><span class="status-pill" [class.finished]="game.result !== '*'">{{ game.result === '*' ? 'Em andamento' : 'Encerrada' }}</span><time>{{ game.updatedAt | date:'dd/MM/yyyy · HH:mm' }}</time></div><h3>{{ game.whiteName }} <span>vs.</span> {{ game.blackName }}</h3><p>{{ game.result === '*' ? 'O tabuleiro está esperando por você.' : terminations[game.termination] }}</p>@if (game.mode === 'COMPUTER') { <p>Stockfish · {{ difficulties[game.difficulty] }}</p> }<div class="history-card-bottom"><span>{{ game.moveCount }} meios-lances <strong>{{ game.result === '*' ? '' : game.result }}</strong></span><button class="button secondary" type="button" [disabled]="store.busy()" (click)="open(game.id)">{{ game.result === '*' ? 'Continuar' : 'Revisar' }} →</button></div></article>
      }</div> }
      @if (store.history().totalPages > 1) { <div class="pagination"><button class="button secondary" type="button" [disabled]="store.history().page === 0 || store.listing()" (click)="store.loadHistory(store.history().page - 1)">← Anterior</button><span>Página {{ store.history().page + 1 }} de {{ store.history().totalPages }}</span><button class="button secondary" type="button" [disabled]="store.history().page + 1 >= store.history().totalPages || store.listing()" (click)="store.loadHistory(store.history().page + 1)">Próxima →</button></div> }
    </section>
    <p class="privacy-note">Partidas vinculadas a este navegador por um cookie local. Limpar os cookies remove o acesso ao histórico. Não é necessário criar uma conta.</p>
  `
})
export class HistoryComponent {
  readonly store = inject(GameStore);
  readonly router = inject(Router);
  readonly terminations = TERMINATIONS;
  readonly difficulties = DIFFICULTIES;
  constructor() { if (this.store.ready()) void this.store.loadHistory(); }
  async open(id: string) { if (await this.store.load(id)) await this.router.navigateByUrl('/play'); }
}
