package dev.chess.infrastructure.engine;

import dev.chess.application.ChessEngine;
import dev.chess.application.EngineException;
import dev.chess.domain.ChessGame;
import dev.chess.domain.Difficulty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.*;

@Component
public class StockfishEngine implements ChessEngine {
    private final String configuredPath;
    private final int timeoutSeconds;
    private final Semaphore capacity = new Semaphore(2);
    private volatile Status cachedStatus;
    private volatile long statusExpires;

    public StockfishEngine(@Value("${chess.engine.path:}") String path,
                           @Value("${chess.engine.timeout-seconds:8}") int timeoutSeconds) {
        this.configuredPath = path;
        this.timeoutSeconds = Math.clamp(timeoutSeconds, 1, 30);
    }

    @Override public synchronized Status status() {
        Status cached = cachedStatus;
        if (cached != null && (System.nanoTime() < statusExpires || capacity.availablePermits() == 0)) return cached;
        try {
            String name = execute(null, null);
            cached = new Status(true, name, "Pronto para jogar.");
        } catch (EngineException e) {
            cached = new Status(false, "Stockfish", "Stockfish indisponível. Instale o motor ou confira STOCKFISH_PATH no backend.");
        }
        cachedStatus = cached;
        statusExpires = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        return cached;
    }

    @Override public ChessGame.MoveInput bestMove(String positionCommand, Difficulty difficulty) {
        // Only a server-generated position command is accepted. Never interpolate client commands.
        if (positionCommand == null || !positionCommand.matches("position startpos(?: moves(?: [a-h][1-8][a-h][1-8][qrbn]?)+)?")) {
            throw new EngineException("Posição inválida para análise.");
        }
        String move = execute(positionCommand, difficulty);
        if (!move.matches("[a-h][1-8][a-h][1-8][qrbn]?")) throw new EngineException("O motor não retornou uma jogada válida.");
        return new ChessGame.MoveInput(move.substring(0, 2), move.substring(2, 4),
                move.length() == 5 ? move.substring(4).toUpperCase(java.util.Locale.ROOT) : "Q");
    }

    private String execute(String positionCommand, Difficulty difficulty) {
        if (!capacity.tryAcquire()) throw new EngineException("O motor está ocupado. Aguarde alguns segundos e tente novamente.");
        Process process = null;
        Future<String> response = null;
        ExecutorService reader = Executors.newVirtualThreadPerTaskExecutor();
        try {
            process = startProcess();
            Process running = process;
            response = reader.submit(() -> communicate(running, positionCommand, difficulty));
            return response.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new EngineException("O computador demorou para responder. Sua partida está salva; tente novamente.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EngineException("A análise foi interrompida. Tente novamente.", e);
        } catch (IOException | ExecutionException e) {
            throw new EngineException("Não foi possível usar o Stockfish. Confira a instalação do motor no backend.", e);
        } finally {
            // Kill before cancelling the reader so blocked reads cannot retain a process or thread.
            if (process != null) process.destroyForcibly();
            if (response != null) response.cancel(true);
            reader.shutdownNow();
            capacity.release();
        }
    }

    protected Process startProcess() throws IOException {
        return new ProcessBuilder(executable()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }

    private String communicate(Process process, String positionCommand, Difficulty difficulty) throws IOException {
        try (var out = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
             var in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            send(out, "uci");
            String name = "Stockfish";
            boolean initialized = false;
            for (String line; (line = in.readLine()) != null;) {
                if (line.startsWith("id name ")) name = line.substring(8);
                if (line.equals("uciok")) { initialized = true; break; }
            }
            if (!initialized) throw new EOFException("Handshake UCI incompleto.");
            if (positionCommand == null) { send(out, "quit"); return name; }
            send(out, "setoption name Threads value 1");
            send(out, "setoption name Hash value 16");
            send(out, "setoption name Skill Level value " + difficulty.skill());
            send(out, "isready");
            await(in, "readyok");
            send(out, "ucinewgame");
            send(out, positionCommand);
            send(out, "go movetime " + difficulty.moveTimeMillis());
            for (String line; (line = in.readLine()) != null;) {
                if (line.startsWith("bestmove ")) {
                    String move = line.split("\\s+")[1]; send(out, "quit"); return move;
                }
            }
            throw new EOFException("Stockfish encerrou antes de responder.");
        }
    }

    private void await(BufferedReader input, String expected) throws IOException {
        for (String line; (line = input.readLine()) != null;) if (line.equals(expected)) return;
        throw new EOFException("Resposta UCI incompleta.");
    }
    private void send(BufferedWriter output, String command) throws IOException { output.write(command); output.newLine(); output.flush(); }

    private String executable() throws IOException {
        if (!configuredPath.isBlank()) return configuredPath;
        Path portable = java.util.List.of(Path.of("tools", "stockfish"), Path.of("backend", "tools", "stockfish"),
                Path.of("..", "backend", "tools", "stockfish")).stream().filter(Files::isDirectory).findFirst().orElse(Path.of("tools", "stockfish"));
        if (Files.isDirectory(portable)) {
            try (var files = Files.walk(portable, 3)) {
                var binary = files.filter(Files::isRegularFile).filter(p -> {
                    String name = p.getFileName().toString();
                    return name.startsWith("stockfish") && (name.endsWith(".exe") || !name.contains(".")) && Files.isExecutable(p);
                }).sorted(Comparator.naturalOrder()).findFirst();
                if (binary.isPresent()) return binary.get().toAbsolutePath().toString();
            }
        }
        return Files.isExecutable(Path.of("/usr/games/stockfish")) ? "/usr/games/stockfish" : "stockfish";
    }
}
