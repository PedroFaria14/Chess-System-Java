package dev.chess.application;

import chess.Color;
import chess.ChessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chess.domain.ChessGame;
import dev.chess.domain.GameMode;
import dev.chess.domain.Difficulty;
import dev.chess.infrastructure.persistence.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class GameService {
    public record Summary(UUID id, long version, String whiteName, String blackName, String result,
                          String termination, int moveCount, Instant createdAt, Instant updatedAt,
                          GameMode mode, Color humanColor, Difficulty difficulty) {}
    public record Page(List<Summary> items, int page, int totalPages, long totalItems) {}
    public record State(UUID id, long version, String whiteName, String blackName, String result,
                        String termination, Color currentPlayer, boolean check, boolean canClaimDraw,
                        String fen, String initialFen, List<ChessGame.Square> board, List<ChessGame.Square> captured,
                        List<ChessGame.Move> moves, Instant createdAt, Instant updatedAt,
                        GameMode mode, Color humanColor, Difficulty difficulty) {}
    private final GameRepository repository;
    private final ObjectMapper mapper;
    private final ChessEngine engine;
    public GameService(GameRepository repository, ObjectMapper mapper, ChessEngine engine) {
        this.repository = repository; this.mapper = mapper; this.engine = engine;
    }

    public State create(UUID owner, String white, String black) {
        return create(owner, white, black, GameMode.LOCAL, Color.WHITE, Difficulty.MEDIUM);
    }

    public State create(UUID owner, String white, String black, GameMode mode, Color humanColor, Difficulty difficulty) {
        if (mode == GameMode.COMPUTER && !engine.status().available()) {
            throw new EngineException("Instale o Stockfish ou configure STOCKFISH_PATH no backend antes de criar uma partida contra o computador.");
        }
        String whiteName = mode == GameMode.COMPUTER && humanColor == Color.BLACK ? "Stockfish" : white.strip();
        String blackName = mode == GameMode.COMPUTER && humanColor == Color.WHITE ? "Stockfish" : black.strip();
        StoredGame stored = new StoredGame(owner, whiteName, blackName);
        stored.configure(mode, humanColor, difficulty);
        repository.saveAndFlush(stored);
        return state(stored, new ChessGame());
    }

    @Transactional(readOnly = true)
    public Page list(UUID owner, int page) {
        var games = repository.findByOwnerIdOrderByUpdatedAtDesc(owner, PageRequest.of(page, 12));
        return new Page(games.getContent().stream().map(this::summary).toList(), page, games.getTotalPages(), games.getTotalElements());
    }

    @Transactional(readOnly = true)
    public State get(UUID owner, UUID id) { StoredGame stored = find(owner, id); return state(stored, restore(stored)); }

    @Transactional(readOnly = true)
    public List<String> legal(UUID owner, UUID id, String square) {
        StoredGame stored = find(owner, id);
        ChessGame game = restore(stored);
        requireHumanTurn(stored, game);
        return game.legalMoves(square);
    }

    public State move(UUID owner, UUID id, long version, ChessGame.MoveInput input) {
        StoredGame stored = current(owner, id, version);
        ChessGame game = restore(stored);
        requireHumanTurn(stored, game);
        game.move(input);
        return save(stored, game);
    }

    public State computerMove(UUID owner, UUID id, long version) {
        StoredGame stored = current(owner, id, version);
        ChessGame game = restore(stored);
        if (stored.getMode() != GameMode.COMPUTER || game.match().getCurrentPlayer() == stored.getHumanColor()) {
            throw new ChessException("Não é a vez do computador nesta partida.");
        }
        if (!game.match().getResult().equals("*")) throw new ChessException("A partida já terminou.");
        if (game.match().canClaimDraw()) {
            game.match().claimDraw();
        } else {
            ChessGame.MoveInput move = engine.bestMove(game.uciPosition(), stored.getDifficulty());
            try { game.move(move); }
            catch (ChessException e) { throw new EngineException("O motor sugeriu uma jogada incompatível. Sua posição foi preservada.", e); }
        }
        return save(stored, game);
    }

    private void requireHumanTurn(StoredGame stored, ChessGame game) {
        if (stored.getMode() == GameMode.COMPUTER && game.match().getCurrentPlayer() != stored.getHumanColor()) {
            throw new ChessException("Aguarde a jogada do computador.");
        }
    }

    public State finish(UUID owner, UUID id, long version, String action) {
        StoredGame stored = current(owner, id, version);
        ChessGame game = restore(stored);
        switch (action) {
            case "resignation" -> {
                Color resigning = stored.getMode() == GameMode.COMPUTER ? stored.getHumanColor() : game.match().getCurrentPlayer();
                game.match().finish(resigning == Color.WHITE ? "0-1" : "1-0", "RESIGNATION");
            }
            case "draw-agreement" -> {
                if (stored.getMode() == GameMode.COMPUTER) throw new ChessException("Contra o computador, empate por acordo não está disponível.");
                game.match().finish("1/2-1/2", "AGREEMENT");
            }
            case "draw-claim" -> { requireHumanTurn(stored, game); game.match().claimDraw(); }
            default -> throw new ChessException("Ação inválida.");
        }
        return save(stored, game);
    }

    @Transactional(readOnly = true)
    public String pgn(UUID owner, UUID id) {
        StoredGame stored = find(owner, id);
        String date = DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneOffset.UTC).format(stored.getCreatedAt());
        return restore(stored).pgn(stored.getWhiteName(), stored.getBlackName(), date,
                stored.getMode() == GameMode.COMPUTER ? "Chess Studio • Stockfish • " + stored.getDifficulty() : "Chess Studio • Local");
    }

    private StoredGame find(UUID owner, UUID id) {
        return repository.findByIdAndOwnerId(id, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partida não encontrada neste navegador."));
    }
    private StoredGame current(UUID owner, UUID id, long version) {
        StoredGame stored = find(owner, id);
        if (stored.getVersion() != version) throw new ResponseStatusException(HttpStatus.CONFLICT, "A partida mudou. Atualize para continuar.");
        return stored;
    }
    private ChessGame restore(StoredGame stored) {
        try {
            ChessGame game = new ChessGame();
            List<ChessGame.MoveInput> moves = mapper.readValue(stored.getMovesJson(), new TypeReference<>() {});
            for (ChessGame.MoveInput move : moves) game.move(move);
            if (game.match().getResult().equals("*") && !stored.getResult().equals("*")) {
                game.match().finish(stored.getResult(), stored.getTermination());
            }
            return game;
        } catch (JsonProcessingException e) { throw new IllegalStateException("Falha ao ler partida persistida.", e); }
    }
    private State save(StoredGame stored, ChessGame game) {
        try {
            stored.update(mapper.writeValueAsString(game.inputs()), game.match().getResult(), game.match().getTermination(), game.moves().size());
            repository.saveAndFlush(stored);
            return state(stored, game);
        } catch (JsonProcessingException e) { throw new IllegalStateException("Falha ao salvar partida.", e); }
    }
    private Summary summary(StoredGame s) {
        return new Summary(s.getId(), s.getVersion(), s.getWhiteName(), s.getBlackName(), s.getResult(), s.getTermination(), s.getMoveCount(), s.getCreatedAt(), s.getUpdatedAt(), s.getMode(), s.getHumanColor(), s.getDifficulty());
    }
    private State state(StoredGame s, ChessGame game) {
        var match = game.match();
        var captured = match.getCapturedPieces().stream().map(p -> new ChessGame.Square(null, p.toString(), p.getColor())).toList();
        return new State(s.getId(), s.getVersion(), s.getWhiteName(), s.getBlackName(), match.getResult(), match.getTermination(),
                match.getCurrentPlayer(), match.getCheck(), match.canClaimDraw(), match.fen(), game.initialFen(), game.board(), captured, game.moves(), s.getCreatedAt(), s.getUpdatedAt(), s.getMode(), s.getHumanColor(), s.getDifficulty());
    }
}
