import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Color, PieceType } from '../core/models';

// Original vector pieces; no remote assets or icon-font dependency.
@Component({
  selector: 'chess-piece',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<svg viewBox="0 0 48 48" aria-hidden="true" focusable="false"
    [attr.fill]="color() === 'WHITE' ? '#f7f2e8' : '#28312e'"
    [attr.stroke]="color() === 'WHITE' ? '#39433b' : '#bac3b5'" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round">
    <path [attr.d]="paths[type()]"/>
    @if (type() === 'N') { <circle cx="28" cy="15" r="1.4" [attr.fill]="color() === 'WHITE' ? '#39433b' : '#f7f2e8'" stroke="none"/> }
    <path d="M13 38h22l2 4H11z"/>
  </svg>`,
  styles: [`:host{display:inline-flex;width:100%;height:100%;align-items:center;justify-content:center;pointer-events:none}svg{width:88%;height:88%;filter:drop-shadow(0 2px 1px #0003)}`]
})
export class PieceComponent {
  readonly type = input.required<PieceType>();
  readonly color = input.required<Color>();
  readonly paths: Record<PieceType, string> = {
    P: 'M24 7a6 6 0 1 1 0 12a6 6 0 1 1 0-12ZM19 20h10l-2 5c0 5 3 8 6 10H15c3-2 6-5 6-10z',
    R: 'M12 7h6v5h4V7h4v5h4V7h6v12H12zM16 19h16l-2 13 4 3H14l4-3zM16 24h16',
    N: 'M14 35c1-8 4-12 12-15l-6 2-4 5-7-5 10-12 7-2 2-5 6 7 1 11-5 9 5 5zM20 12l5 1M14 35h21',
    B: 'M24 4l2 4c7 4 10 8 7 13-2 3-5 5-9 5s-7-2-9-5c-3-5 0-9 7-13zM25 11l-5 9M20 27h8l-1 3 7 5H14l7-5z',
    Q: 'M11 12l7 7 6-11 6 11 7-7-5 15H16zM17 29h14l-1 3 5 3H13l5-3zM8 10a2 2 0 1 1 4 0a2 2 0 1 1-4 0ZM22 6a2 2 0 1 1 4 0a2 2 0 1 1-4 0ZM36 10a2 2 0 1 1 4 0a2 2 0 1 1-4 0Z',
    K: 'M22 3h4v5h5v4h-5v5h-4v-5h-5V8h5zM24 20c-10-9-18 0-11 8l5 4h12l5-4c7-8-1-17-11-8ZM18 33h12l4 2H14z'
  };
}
