import { effect, Injectable, signal } from '@angular/core';

export function readStorage(key: string): string | null {
  try { return localStorage.getItem(key); } catch { return null; }
}
export function writeStorage(key: string, value: string): void {
  try { localStorage.setItem(key, value); } catch { /* Gameplay also works with storage disabled. */ }
}

@Injectable({ providedIn: 'root' })
export class Preferences {
  readonly theme = signal(readStorage('chess-theme') === 'wood' ? 'wood' : 'moss');
  readonly hints = signal(readStorage('chess-hints') !== 'false');
  readonly sound = signal(readStorage('chess-sound') === 'true');
  private audio?: AudioContext;
  constructor() {
    effect(() => {
      document.documentElement.dataset['boardTheme'] = this.theme();
      writeStorage('chess-theme', this.theme());
      writeStorage('chess-hints', String(this.hints()));
      writeStorage('chess-sound', String(this.sound()));
    });
  }
  async playMove() {
    if (!this.sound()) return;
    try {
      this.audio ??= new AudioContext();
      await this.audio.resume();
      const tone = this.audio.createOscillator(), gain = this.audio.createGain();
      tone.connect(gain); gain.connect(this.audio.destination);
      tone.frequency.setValueAtTime(440, this.audio.currentTime);
      tone.frequency.exponentialRampToValueAtTime(220, this.audio.currentTime + .08);
      gain.gain.setValueAtTime(.06, this.audio.currentTime);
      gain.gain.exponentialRampToValueAtTime(.001, this.audio.currentTime + .1);
      tone.start(); tone.stop(this.audio.currentTime + .1);
    } catch { /* A browser may block audio; the move remains valid. */ }
  }
}
