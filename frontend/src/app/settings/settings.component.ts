import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Preferences } from '../core/preferences.service';
import { BoardComponent } from '../shared/board.component';
import { INITIAL_FEN } from '../core/models';

@Component({
  selector: 'app-settings',
  imports: [BoardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page-heading"><div><span class="eyebrow">DO SEU JEITO</span><h1>Seu tabuleiro. Seu estilo.</h1><p>Pequenos detalhes para se sentir em casa.</p></div></section>
    <div class="settings-layout"><section class="settings-panel"><h2>A experiência de jogo</h2><fieldset><legend>Tema do tabuleiro</legend><p>Escolha a combinação que deixa seu jogo mais confortável.</p><div class="theme-options"><button type="button" class="theme-choice" [class.chosen]="preferences.theme() === 'moss'" [attr.aria-pressed]="preferences.theme() === 'moss'" (click)="preferences.theme.set('moss')"><span class="swatch moss"></span>Verde musgo @if (preferences.theme() === 'moss') { <span>✓</span> }</button><button type="button" class="theme-choice" [class.chosen]="preferences.theme() === 'wood'" [attr.aria-pressed]="preferences.theme() === 'wood'" (click)="preferences.theme.set('wood')"><span class="swatch wood"></span>Madeira @if (preferences.theme() === 'wood') { <span>✓</span> }</button></div></fieldset>
      <div class="setting-row"><div><label for="hints">Movimentos guiados</label><p>Mostre os destinos legais da peça selecionada.</p></div><input id="hints" type="checkbox" role="switch" [checked]="preferences.hints()" (change)="preferences.hints.set($any($event.target).checked)"></div>
      <div class="setting-row"><div><label for="sound">Som de movimento</label><p>Um toque discreto a cada lance realizado.</p></div><input id="sound" type="checkbox" role="switch" [checked]="preferences.sound()" (change)="preferences.sound.set($any($event.target).checked)"></div>
      <p class="save-note" role="status">✓ Preferências salvas automaticamente neste navegador.</p>
    </section><section class="settings-preview" aria-label="Prévia do tema"><chess-board [fen]="initialFen"/><p>O mesmo jogo. Um novo olhar.</p></section></div>
  `
})
export class SettingsComponent {
  readonly preferences = inject(Preferences);
  readonly initialFen = INITIAL_FEN;
}
