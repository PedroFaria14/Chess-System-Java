import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { GameStore } from './core/game-store.service';
import { PieceComponent } from './shared/piece.component';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, PieceComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="skip-link" href="#main">Pular para o conteúdo</a>
    <aside class="sidebar">
      <a class="brand" routerLink="/play" aria-label="Chess Studio, jogar"><span class="brand-mark"><chess-piece type="N" color="WHITE"/></span><span>chess<span class="brand-light">studio</span><small>CADA LANCE CONTA.</small></span></a>
      <span class="nav-caption">SEU ESPAÇO</span>
      <nav aria-label="Navegação principal">
        <a routerLink="/play" routerLinkActive="active"><span aria-hidden="true">▦</span> Jogar <span class="nav-arrow" aria-hidden="true">↗</span></a>
        <a routerLink="/history" routerLinkActive="active"><span aria-hidden="true">◷</span> Minhas partidas</a>
        <a routerLink="/settings" routerLinkActive="active"><span aria-hidden="true">⚙</span> Preferências</a>
      </nav>
      <div class="sidebar-note"><span class="tiny-board" aria-hidden="true">▧</span><p>Uma boa partida<br>começa com um lance.</p><small>Jogue no seu ritmo.</small></div>
      <footer class="sidebar-footer"><span class="online-dot"></span> Seu jogo. Seu próximo desafio.<small>FEITO PARA O JOGO.</small></footer>
    </aside>
    <div class="workspace">
      <header class="topbar"><span>Seu espaço para pensar e jogar.</span><span class="local-badge"><span class="online-dot"></span> {{ store.game()?.mode === 'COMPUTER' ? 'Contra Stockfish' : 'Modo local' }}</span></header>
      <main id="main">
        @if (store.error()) { <div class="alert error" role="alert"><span>{{ store.error() }}</span><button type="button" aria-label="Fechar mensagem de erro" (click)="store.error.set('')">×</button></div> }
        <router-outlet/>
      </main>
      <footer class="page-footer"><span>Chess Studio</span><span>Menos distração. Mais possibilidades.</span><span>Java + Angular</span></footer>
    </div>
    <div class="sr-only" role="status" aria-live="polite">{{ store.notice() }}</div>
  `
})
export class AppComponent { readonly store = inject(GameStore); }
