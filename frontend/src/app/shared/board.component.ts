import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { Piece, PieceType, PIECE_NAMES } from '../core/models';
import { PieceComponent } from './piece.component';

@Component({
  selector: 'chess-board',
  imports: [PieceComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="board" role="group" aria-label="Tabuleiro de xadrez. Use as setas para navegar e Enter para selecionar." (keydown)="navigate($event)">
      @for (square of squares(); track square.name; let i = $index) {
        <button type="button" class="square" [class.dark]="square.dark" [class.selected]="selected() === square.name"
          [class.last]="lastFrom() === square.name || lastTo() === square.name"
          [class.checked]="check() && square.piece?.type === 'K' && square.piece?.color === turn()"
          [class.legal]="hints() && legal().includes(square.name)" [class.occupied]="!!square.piece"
          [attr.aria-label]="label(square.name, square.piece)" [attr.aria-pressed]="selected() === square.name"
          [attr.aria-disabled]="!interactive()" [attr.tabindex]="focusIndex() === i ? 0 : -1" [attr.data-square]="square.name"
          [draggable]="interactive() && square.piece?.color === turn()"
          (focus)="focusIndex.set(i)" (click)="activate.emit(square.name)"
          (dragstart)="dragStart($event, square.name)" (dragover)="$event.preventDefault()" (drop)="drop($event, square.name)">
          @if (i % 8 === 0) { <span class="rank" aria-hidden="true">{{ square.name[1] }}</span> }
          @if (i >= 56) { <span class="file" aria-hidden="true">{{ square.name[0] }}</span> }
          @if (square.piece; as piece) { <chess-piece [type]="piece.type" [color]="piece.color"/> }
          @if (hints() && legal().includes(square.name)) { <span class="target" aria-hidden="true"></span> }
        </button>
      }
    </div>`,
  styles: [`
    :host{display:block;width:100%;min-width:0}.board{display:grid;grid-template-columns:repeat(8,minmax(0,1fr));aspect-ratio:1;overflow:hidden;border-radius:5px;box-shadow:0 16px 48px #0003}
    .square{border:0;border-radius:0;position:relative;padding:3%;aspect-ratio:1;background:var(--square-light);color:var(--square-dark);cursor:pointer;min-width:0;min-height:0;touch-action:manipulation}
    .square.dark{background:var(--square-dark);color:var(--square-light)}.square.last{background:var(--square-last-light)}.square.last.dark{background:var(--square-last-dark)}
    .square.selected{background:#d6c668}.square.checked{background:radial-gradient(ellipse,#d66b60 20%,#93413c 100%)}.square[aria-disabled=true]{cursor:default}
    .square:focus-visible{outline:3px solid #ffe39a;outline-offset:-4px;z-index:2}.square[draggable=true]{cursor:grab}.rank,.file{position:absolute;font-size:clamp(9px,1.1vw,12px);font-weight:700;line-height:1;z-index:1}.rank{left:5px;top:5px}.file{right:5px;bottom:4px}
    .target{position:absolute;width:24%;height:24%;background:#1c302d55;border-radius:50%;top:38%;left:38%;pointer-events:none}.occupied .target{inset:3%;width:auto;height:auto;border:5px solid #173a3977;background:none}
    chess-piece{transition:transform 120ms ease}.square:hover[draggable=true] chess-piece{transform:translateY(-2px)}
    @media(prefers-reduced-motion:reduce){chess-piece{transition:none}}
  `]
})
export class BoardComponent {
  readonly fen = input.required<string>();
  readonly flipped = input(false);
  readonly selected = input<string | null>(null);
  readonly legal = input<string[]>([]);
  readonly interactive = input(false);
  readonly hints = input(true);
  readonly check = input(false);
  readonly turn = input('WHITE');
  readonly lastFrom = input<string | null>(null);
  readonly lastTo = input<string | null>(null);
  readonly activate = output<string>();
  readonly dropped = output<{ from: string; to: string }>();
  readonly focusIndex = signal(56);
  readonly squares = computed(() => {
    const board = new Map<string, Piece>();
    this.fen().split(' ')[0].split('/').forEach((rank, r) => {
      let c = 0;
      for (const symbol of rank) {
        if (/\d/.test(symbol)) { c += Number(symbol); continue; }
        const square = String.fromCharCode(97 + c) + (8 - r);
        board.set(square, { square, type: symbol.toUpperCase() as PieceType, color: symbol === symbol.toUpperCase() ? 'WHITE' : 'BLACK' }); c++;
      }
    });
    return Array.from({ length: 64 }, (_, i) => {
      const index = this.flipped() ? 63 - i : i;
      const r = Math.floor(index / 8), c = index % 8, name = String.fromCharCode(97 + c) + (8 - r);
      return { name, dark: (r + c) % 2 === 1, piece: board.get(name) ?? null };
    });
  });
  label(square: string, piece: Piece | null) {
    return `${square}, ${piece ? PIECE_NAMES[piece.type] + (piece.color === 'WHITE' ? ' branco' : ' preto') : 'vazia'}${this.legal().includes(square) ? ', movimento possível' : ''}`;
  }
  navigate(event: KeyboardEvent) {
    const offsets: Record<string, number> = { ArrowRight: 1, ArrowLeft: -1, ArrowUp: -8, ArrowDown: 8 };
    if (event.key === 'Escape') { this.activate.emit(''); return; }
    if (!(event.key in offsets)) return;
    event.preventDefault();
    const next = Math.max(0, Math.min(63, this.focusIndex() + offsets[event.key]));
    this.focusIndex.set(next);
    const parent = (event.target as HTMLElement).closest('.board');
    (parent?.children[next] as HTMLButtonElement | undefined)?.focus();
  }
  dragStart(event: DragEvent, from: string) {
    if (!this.interactive()) { event.preventDefault(); return; }
    event.dataTransfer?.setData('text/plain', from);
    if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move';
  }
  drop(event: DragEvent, to: string) {
    event.preventDefault();
    const from = event.dataTransfer?.getData('text/plain');
    if (this.interactive() && from && /^[a-h][1-8]$/.test(from)) this.dropped.emit({ from, to });
  }
}
