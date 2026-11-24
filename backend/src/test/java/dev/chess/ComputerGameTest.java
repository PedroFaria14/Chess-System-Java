package dev.chess;

import chess.Color;
import chess.ChessException;
import dev.chess.application.*;
import dev.chess.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:computer-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}"})
class ComputerGameTest {
    @Autowired GameService service;
    @MockitoBean ChessEngine engine;
    @BeforeEach void ready() { when(engine.status()).thenReturn(new ChessEngine.Status(true, "Stockfish", "ready")); }

    @Test void humanAndComputerHaveSeparateTurnsAndStaleRequestsCannotReplayEngineMoves() {
        UUID owner = UUID.randomUUID();
        var game = service.create(owner, "Ana", "Ignored", GameMode.COMPUTER, Color.WHITE, Difficulty.EASY);
        assertThat(game.blackName()).isEqualTo("Stockfish");
        assertThatThrownBy(() -> service.computerMove(owner, game.id(), 0)).isInstanceOf(ChessException.class);
        var afterHuman = service.move(owner, game.id(), 0, new ChessGame.MoveInput("e2", "e4", "Q"));
        assertThatThrownBy(() -> service.move(owner, game.id(), afterHuman.version(), new ChessGame.MoveInput("e7", "e5", "Q")))
                .isInstanceOf(ChessException.class).hasMessageContaining("Aguarde");
        when(engine.bestMove("position startpos moves e2e4", Difficulty.EASY)).thenReturn(new ChessGame.MoveInput("e7", "e5", "Q"));
        var afterComputer = service.computerMove(owner, game.id(), afterHuman.version());
        assertThat(afterComputer.moves()).hasSize(2);
        assertThat(afterComputer.currentPlayer()).isEqualTo(Color.WHITE);
        assertThatThrownBy(() -> service.computerMove(owner, game.id(), afterHuman.version()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(engine, times(1)).bestMove(anyString(), any());
    }

    @Test void computerCanOpenWhenHumanChoosesBlackAndResignationAlwaysBelongsToHuman() {
        UUID owner = UUID.randomUUID();
        var game = service.create(owner, "Ignored", "Ana", GameMode.COMPUTER, Color.BLACK, Difficulty.HARD);
        when(engine.bestMove("position startpos", Difficulty.HARD)).thenReturn(new ChessGame.MoveInput("d2", "d4", "Q"));
        assertThat(service.computerMove(owner, game.id(), game.version()).moves().getFirst().san()).isEqualTo("d4");
        var other = service.create(owner, "Ignored", "Ana", GameMode.COMPUTER, Color.BLACK, Difficulty.HARD);
        assertThat(service.finish(owner, other.id(), 0, "resignation").result()).isEqualTo("1-0");
    }

    @Test void engineFailureOrIllegalOutputPreservesLastSavedPosition() {
        UUID owner = UUID.randomUUID();
        var game = service.create(owner, "Stockfish", "Ana", GameMode.COMPUTER, Color.BLACK, Difficulty.MEDIUM);
        when(engine.bestMove(anyString(), any())).thenThrow(new EngineException("timeout"));
        assertThatThrownBy(() -> service.computerMove(owner, game.id(), 0)).isInstanceOf(EngineException.class);
        assertThat(service.get(owner, game.id()).version()).isZero();
        doReturn(new ChessGame.MoveInput("e2", "e5", "Q")).when(engine).bestMove(anyString(), any());
        assertThatThrownBy(() -> service.computerMove(owner, game.id(), 0)).isInstanceOf(EngineException.class);
        assertThat(service.get(owner, game.id()).moves()).isEmpty();
        assertThatThrownBy(() -> service.finish(owner, game.id(), 0, "draw-agreement")).isInstanceOf(ChessException.class);
    }

    @Test void missingEngineDoesNotPreventLocalGames() {
        when(engine.status()).thenReturn(new ChessEngine.Status(false, "Stockfish", "missing"));
        UUID owner = UUID.randomUUID();
        assertThat(service.create(owner, "Ana", "Pedro").mode()).isEqualTo(GameMode.LOCAL);
        assertThatThrownBy(() -> service.create(owner, "Ana", "Stockfish", GameMode.COMPUTER, Color.WHITE, Difficulty.EASY))
                .isInstanceOf(EngineException.class);
    }
}
