export type Color = 'WHITE' | 'BLACK';
export type GameMode = 'LOCAL' | 'COMPUTER';
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';
export interface EngineStatus { available: boolean; name: string; message: string; }
export interface GameSettings { whiteName: string; blackName: string; mode: GameMode; humanColor: Color; difficulty: Difficulty; }
export const DIFFICULTIES: Record<Difficulty, string> = { EASY: 'Fácil', MEDIUM: 'Médio', HARD: 'Difícil' };
export type PieceType = 'K' | 'Q' | 'R' | 'B' | 'N' | 'P';
export interface Piece { square: string | null; type: PieceType; color: Color; }
export interface Move { ply: number; from: string; to: string; promotion: string; san: string; fen: string; }
export interface GameSummary {
  id: string; version: number; whiteName: string; blackName: string;
  result: string; termination: string; moveCount: number; createdAt: string; updatedAt: string;
  mode: GameMode; humanColor: Color; difficulty: Difficulty;
}
export interface Game extends Omit<GameSummary, 'moveCount'> {
  currentPlayer: Color; check: boolean; canClaimDraw: boolean; fen: string; initialFen: string;
  board: Piece[]; captured: Piece[]; moves: Move[];
}
export interface GamePage { items: GameSummary[]; page: number; totalPages: number; totalItems: number; }
export const INITIAL_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';
export const PIECE_NAMES: Record<PieceType, string> = { K: 'Rei', Q: 'Dama', R: 'Torre', B: 'Bispo', N: 'Cavalo', P: 'Peão' };
export const TERMINATIONS: Record<string, string> = {
  IN_PROGRESS: 'Em andamento', CHECKMATE: 'Xeque-mate', STALEMATE: 'Empate por afogamento',
  INSUFFICIENT_MATERIAL: 'Material insuficiente', FIVEFOLD_REPETITION: 'Repetição quíntupla',
  THREEFOLD_REPETITION: 'Tripla repetição', FIFTY_MOVES: 'Regra dos 50 lances',
  SEVENTY_FIVE_MOVES: 'Regra dos 75 lances', AGREEMENT: 'Empate por acordo', RESIGNATION: 'Desistência'
};
