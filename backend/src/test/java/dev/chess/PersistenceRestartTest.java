package dev.chess;

import dev.chess.application.GameService;
import dev.chess.domain.ChessGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import java.nio.file.Path;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class PersistenceRestartTest {
    @TempDir Path directory;
    @Test void restartingApplicationPreservesPositionAndEnPassantRights() {
        UUID owner = UUID.randomUUID();
        UUID id;
        String fen;
        try (var context = open()) {
            GameService service = context.getBean(GameService.class);
            var state = service.create(owner, "Ana", "Pedro"); id = state.id();
            for (String[] move : new String[][]{{"e2","e4"},{"a7","a6"},{"e4","e5"},{"d7","d5"}}) {
                state = service.move(owner, id, state.version(), new ChessGame.MoveInput(move[0], move[1], "Q"));
            }
            fen = state.fen();
        }
        try (var context = open()) {
            GameService service = context.getBean(GameService.class);
            var state = service.get(owner, id);
            assertThat(state.fen()).isEqualTo(fen);
            assertThat(state.moves()).hasSize(4);
            assertThat(service.legal(owner, id, "e5")).contains("d6");
            var captured = service.move(owner, id, state.version(), new ChessGame.MoveInput("e5", "d6", "Q"));
            assertThat(captured.captured()).hasSize(1);
        }
    }
    private ConfigurableApplicationContext open() {
        return new SpringApplicationBuilder(ChessApplication.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=jdbc:h2:file:" + directory.resolve("restart").toString().replace('\\', '/') + ";MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE",
                "--spring.datasource.username=sa", "--spring.datasource.password=", "--spring.main.banner-mode=off", "--logging.level.root=ERROR");
    }
}
