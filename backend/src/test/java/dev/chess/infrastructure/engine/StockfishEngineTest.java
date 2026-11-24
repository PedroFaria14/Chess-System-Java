package dev.chess.infrastructure.engine;

import dev.chess.application.EngineException;
import dev.chess.domain.Difficulty;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

class StockfishEngineTest {
    @Test void uciHandshakeAndPromotionAreDecoded() {
        StockfishEngine engine = fake("normal");
        assertThat(engine.status().available()).isTrue();
        var move = engine.bestMove("position startpos moves a2a4", Difficulty.EASY);
        assertThat(move.from()).isEqualTo("a7");
        assertThat(move.to()).isEqualTo("a8");
        assertThat(move.promotion()).isEqualTo("N");
    }
    @Test void malformedMoveAndDeadProcessAreRejected() {
        assertThatThrownBy(() -> fake("malformed").bestMove("position startpos", Difficulty.MEDIUM)).isInstanceOf(EngineException.class);
        assertThat(fake("eof").status().available()).isFalse();
        assertThatThrownBy(() -> fake("normal").bestMove("position startpos\nquit", Difficulty.EASY)).isInstanceOf(EngineException.class);
    }
    @Test void timeoutTerminatesChildProcess() throws Exception {
        Process[] child = new Process[1];
        StockfishEngine engine = new StockfishEngine("", 1) {
            @Override protected Process startProcess() throws IOException { child[0] = launch("hang"); return child[0]; }
        };
        assertThatThrownBy(() -> engine.bestMove("position startpos", Difficulty.HARD)).isInstanceOf(EngineException.class).hasMessageContaining("demorou");
        assertThat(child[0].waitFor(3, TimeUnit.SECONDS)).isTrue();
        assertThat(child[0].isAlive()).isFalse();
    }
    private StockfishEngine fake(String behavior) {
        return new StockfishEngine("", 3) {
            @Override protected Process startProcess() throws IOException { return launch(behavior); }
        };
    }
    private Process launch(String behavior) throws IOException {
        String binary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return new ProcessBuilder(binary, "-cp", System.getProperty("java.class.path"), FakeUci.class.getName(), behavior)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }
    public static class FakeUci {
        public static void main(String[] args) throws Exception {
            if (args[0].equals("eof")) return;
            try (var input = new BufferedReader(new InputStreamReader(System.in))) {
                for (String line; (line = input.readLine()) != null;) {
                    if (line.equals("uci")) System.out.println("id name Test engine\nuciok");
                    if (line.equals("isready")) System.out.println("readyok");
                    if (line.startsWith("go")) {
                        if (args[0].equals("hang")) Thread.sleep(30000);
                        else System.out.println("bestmove " + (args[0].equals("normal") ? "a7a8n" : "invalid"));
                    }
                    if (line.equals("quit")) return;
                    System.out.flush();
                }
            }
        }
    }
}
