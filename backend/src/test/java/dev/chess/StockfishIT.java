package dev.chess;

import dev.chess.domain.ChessGame;
import dev.chess.domain.Difficulty;
import dev.chess.infrastructure.engine.StockfishEngine;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Run explicitly with `mvnw verify -Pstockfish` after installing the real binary. */
class StockfishIT {
    @Test void realStockfishReturnsLegalMovesAtEveryDifficulty() {
        String path = System.getenv().getOrDefault("STOCKFISH_PATH", "");
        StockfishEngine engine = new StockfishEngine(path, 8);
        assertThat(engine.status().available()).isTrue();
        assertThat(engine.status().name()).containsIgnoringCase("Stockfish");
        for (Difficulty difficulty : Difficulty.values()) {
            ChessGame game = new ChessGame();
            for (int ply = 0; ply < 4; ply++) game.move(engine.bestMove(game.uciPosition(), difficulty));
            assertThat(game.moves()).hasSize(4);
        }
    }
}
