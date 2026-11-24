package dev.chess.application;

import dev.chess.domain.ChessGame;
import dev.chess.domain.Difficulty;

/** Infrastructure chooses a move; the domain still validates it before persistence. */
public interface ChessEngine {
    record Status(boolean available, String name, String message) {}
    Status status();
    ChessGame.MoveInput bestMove(String positionCommand, Difficulty difficulty);
}
