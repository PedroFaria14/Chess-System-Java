import { ChangeDetectionStrategy, Component, EventEmitter, inject, Input, OnChanges, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Color, Difficulty, Game, GameMode, GameSettings } from '../core/models';
import { GameStore } from '../core/game-store.service';

@Component({
  selector: 'game-setup',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form (ngSubmit)="submit()" #setup="ngForm">
      <fieldset class="game-options" [disabled]="store.busy()">
        <legend class="sr-only">Configuração da partida</legend>
        <div class="mode-selector" aria-label="Modo de jogo">
          <button type="button" [attr.aria-pressed]="mode === 'LOCAL'" (click)="mode = 'LOCAL'">Com um amigo</button>
          <button type="button" [attr.aria-pressed]="mode === 'COMPUTER'" (click)="mode = 'COMPUTER'">Contra o computador</button>
        </div>
        @if (mode === 'LOCAL') {
          <label [for]="prefix + '-white'">○ Jogador das brancas</label><input [id]="prefix + '-white'" name="white" [(ngModel)]="whiteName" maxlength="40" required autocomplete="off">
          <label [for]="prefix + '-black'">● Jogador das pretas</label><input [id]="prefix + '-black'" name="black" [(ngModel)]="blackName" maxlength="40" required autocomplete="off">
        } @else {
          <label [for]="prefix + '-human'">Seu nome</label><input [id]="prefix + '-human'" name="human" [(ngModel)]="humanName" maxlength="40" required autocomplete="off">
          <div class="setup-selects"><div><label [for]="prefix + '-color'">Sua cor</label><select [id]="prefix + '-color'" name="color" [(ngModel)]="humanColor"><option value="WHITE">○ Brancas</option><option value="BLACK">● Pretas</option></select></div>
          <div><label [for]="prefix + '-difficulty'">Dificuldade</label><select [id]="prefix + '-difficulty'" name="difficulty" [(ngModel)]="difficulty"><option value="EASY">Fácil</option><option value="MEDIUM">Médio</option><option value="HARD">Difícil</option></select></div></div>
          <p class="engine-note" [class.unavailable]="!store.engineStatus()?.available" role="status">{{ store.engineStatus()?.available ? '✓ ' + store.engineStatus()?.name + ' pronto para jogar.' : store.engineStatus()?.message ?? 'Consultando Stockfish…' }}</p>
          @if (!store.engineStatus()?.available) { <button type="button" class="engine-refresh" (click)="store.loadEngineStatus()">↻ Verificar motor novamente</button> }
        }
        <button class="button primary full" type="submit" [disabled]="setup.invalid || !validNames() || store.busy() || (mode === 'COMPUTER' && !store.engineStatus()?.available)">{{ store.busy() ? 'Criando partida…' : 'Começar partida' }} <span aria-hidden="true">→</span></button>
      </fieldset>
    </form>
  `
})
export class GameSetupComponent implements OnChanges {
  readonly store = inject(GameStore);
  @Input() initial: Game | null = null;
  @Input() prefix = 'setup';
  @Output() readonly configured = new EventEmitter<GameSettings>();
  mode: GameMode = 'LOCAL';
  humanColor: Color = 'WHITE';
  difficulty: Difficulty = 'MEDIUM';
  whiteName = 'Jogador 1'; blackName = 'Jogador 2'; humanName = 'Jogador 1';
  ngOnChanges() {
    if (!this.initial) return;
    this.mode = this.initial.mode; this.humanColor = this.initial.humanColor; this.difficulty = this.initial.difficulty;
    this.whiteName = this.initial.whiteName === 'Stockfish' ? 'Jogador 1' : this.initial.whiteName;
    this.blackName = this.initial.blackName === 'Stockfish' ? 'Jogador 2' : this.initial.blackName;
    this.humanName = this.initial.humanColor === 'WHITE' ? this.whiteName : this.blackName;
  }
  validNames() { return this.mode === 'COMPUTER' ? !!this.humanName.trim() : !!this.whiteName.trim() && !!this.blackName.trim(); }
  submit() {
    this.configured.emit({ mode: this.mode, humanColor: this.humanColor, difficulty: this.difficulty,
      whiteName: this.mode === 'LOCAL' ? this.whiteName : this.humanColor === 'WHITE' ? this.humanName : 'Stockfish',
      blackName: this.mode === 'LOCAL' ? this.blackName : this.humanColor === 'BLACK' ? this.humanName : 'Stockfish' });
  }
}
