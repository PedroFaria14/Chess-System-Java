import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, timeout } from 'rxjs';
import { EngineStatus, Game, GamePage, GameSettings } from './models';

@Injectable({ providedIn: 'root' })
export class GameApi {
  private readonly http = inject(HttpClient);
  list(page = 0) { return firstValueFrom(this.http.get<GamePage>('/api/games', { params: { page } }).pipe(timeout(15000))); }
  get(id: string) { return firstValueFrom(this.http.get<Game>(`/api/games/${id}`).pipe(timeout(15000))); }
  engineStatus() { return firstValueFrom(this.http.get<EngineStatus>('/api/engine').pipe(timeout(15000))); }
  create(settings: GameSettings) {
    return firstValueFrom(this.http.post<Game>('/api/games', settings).pipe(timeout(15000)));
  }
  computerMove(game: Game) {
    return firstValueFrom(this.http.post<Game>(`/api/games/${game.id}/computer-move`, { version: game.version }).pipe(timeout(15000)));
  }
  legal(id: string, from: string) {
    return firstValueFrom(this.http.get<string[]>(`/api/games/${id}/legal-moves`, { params: { from } }).pipe(timeout(15000)));
  }
  move(game: Game, from: string, to: string, promotion: string) {
    return firstValueFrom(this.http.post<Game>(`/api/games/${game.id}/moves`, { version: game.version, from, to, promotion }).pipe(timeout(15000)));
  }
  finish(game: Game, action: string) {
    return firstValueFrom(this.http.post<Game>(`/api/games/${game.id}/${action}`, { version: game.version }).pipe(timeout(15000)));
  }
  pgn(id: string) { return firstValueFrom(this.http.get(`/api/games/${id}/pgn`, { responseType: 'text' }).pipe(timeout(15000))); }
}
