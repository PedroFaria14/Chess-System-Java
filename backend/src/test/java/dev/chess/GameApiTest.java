package dev.chess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chess.application.GameService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:chess-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}"})
@AutoConfigureMockMvc
class GameApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired GameService service;

    @Test void createPlayRestoreExportAndRejectStaleOrForeignRequests() throws Exception {
        var created = mvc.perform(post("/api/games").contentType("application/json")
                        .content("{\"whiteName\":\"Ana\",\"blackName\":\"Pedro\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(0)).andReturn();
        Cookie cookie = created.getResponse().getCookie("chess-owner");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        JsonNode initial = mapper.readTree(created.getResponse().getContentAsString());
        String id = initial.get("id").asText();
        mvc.perform(get("/api/games/" + id + "/legal-moves").param("from", "e2").cookie(cookie))
                .andExpect(status().isOk()).andExpect(content().json("[\"e4\",\"e3\"]", false));
        String move = "{\"from\":\"e2\",\"to\":\"e4\",\"promotion\":\"Q\",\"version\":0}";
        mvc.perform(post("/api/games/" + id + "/moves").cookie(cookie).contentType("application/json").content(move))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.moves[0].san").value("e4"));
        mvc.perform(post("/api/games/" + id + "/moves").cookie(cookie).contentType("application/json").content(move))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/games/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/games/" + id + "/pgn").cookie(cookie))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("1. e4 *")));
        // A fresh reconstruction from persisted moves retains the en-passant target and turn.
        var restored = service.get(UUID.fromString(cookie.getValue()), UUID.fromString(id));
        assertThat(restored.fen()).endsWith("b KQkq e3 0 1");
        mvc.perform(post("/api/games/" + id + "/resignation").cookie(cookie).contentType("application/json").content("{\"version\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("1-0"));
        mvc.perform(get("/api/games/" + id).cookie(cookie)).andExpect(jsonPath("$.termination").value("RESIGNATION"));
    }

    @Test void invalidInputAndCrossSiteWritesAreRejected() throws Exception {
        mvc.perform(post("/api/games").contentType("application/json").content("{\"whiteName\":\"\",\"blackName\":\"Pedro\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/games").header("Origin", "https://other.example").contentType("application/json")
                        .content("{\"whiteName\":\"Ana\",\"blackName\":\"Pedro\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/games").param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test void illegalMoveDoesNotChangePersistedVersion() throws Exception {
        UUID owner = UUID.randomUUID();
        var game = service.create(owner, "Brancas", "Pretas");
        Cookie cookie = new Cookie("chess-owner", owner.toString());
        mvc.perform(post("/api/games/" + game.id() + "/moves").cookie(cookie).contentType("application/json")
                        .content("{\"from\":\"e2\",\"to\":\"e5\",\"promotion\":\"Q\",\"version\":0}"))
                .andExpect(status().isUnprocessableEntity());
        assertThat(service.get(owner, game.id()).version()).isZero();
    }

    @Test void concurrentMovesOnlyPersistOneVersion() throws Exception {
        UUID owner = UUID.randomUUID();
        var game = service.create(owner, "Ana", "Pedro");
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> attempt(owner, game.id(), "e2", "e4", start));
            var second = pool.submit(() -> attempt(owner, game.id(), "d2", "d4", start));
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        }
        assertThat(service.get(owner, game.id()).moves()).hasSize(1);
        assertThat(service.get(owner, game.id()).version()).isEqualTo(1);
    }

    private boolean attempt(UUID owner, UUID id, String from, String to, CountDownLatch start) throws Exception {
        start.await();
        try {
            service.move(owner, id, 0, new dev.chess.domain.ChessGame.MoveInput(from, to, "Q")); return true;
        } catch (org.springframework.dao.OptimisticLockingFailureException | org.springframework.web.server.ResponseStatusException expected) {
            return false;
        }
    }
}
