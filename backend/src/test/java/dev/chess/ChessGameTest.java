package dev.chess;

import dev.chess.domain.ChessGame;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ChessGameTest {
    @Test void notationDisambiguatesLegalKnightsAndExportEscapesNames() {
        ChessGame game = new ChessGame();
        move(game, "g1", "f3"); move(game, "g8", "f6");
        move(game, "d2", "d3"); move(game, "d7", "d6");
        move(game, "b1", "d2");
        assertThat(game.moves().getLast().san()).isEqualTo("Nbd2");
        assertThat(game.pgn("Ana \"A\"", "Pedro", "2026.11.24"))
                .contains("[White \"Ana \\\"A\\\"\"]", "1. Nf3 Nf6 2. d3 d6 3. Nbd2 *");
    }
    @Test void castlingNotationAndCaptureNotationAreStandard() {
        ChessGame game = new ChessGame();
        move(game, "e2", "e4"); move(game, "e7", "e5");
        move(game, "g1", "f3"); move(game, "b8", "c6");
        move(game, "f1", "c4"); move(game, "f8", "c5");
        move(game, "e1", "g1");
        assertThat(game.moves().getLast().san()).isEqualTo("O-O");
    }
    private void move(ChessGame game, String from, String to) { game.move(new ChessGame.MoveInput(from, to, "Q")); }
}
