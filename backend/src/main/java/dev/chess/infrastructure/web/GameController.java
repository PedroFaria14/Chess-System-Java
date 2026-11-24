package dev.chess.infrastructure.web;

import dev.chess.application.GameService;
import dev.chess.domain.ChessGame;
import dev.chess.domain.GameMode;
import dev.chess.domain.Difficulty;
import chess.Color;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/games")
@Validated
public class GameController {
    public record Create(@NotBlank @Size(max = 40) @Pattern(regexp = "[^\\p{Cntrl}]+") String whiteName,
                         @NotBlank @Size(max = 40) @Pattern(regexp = "[^\\p{Cntrl}]+") String blackName,
                         GameMode mode, Color humanColor, Difficulty difficulty) {}
    public record Move(@NotNull @Min(0) Long version, @NotNull @Pattern(regexp = "[a-h][1-8]") String from,
                       @NotNull @Pattern(regexp = "[a-h][1-8]") String to, @NotNull @Pattern(regexp = "[QRBN]") String promotion) {}
    public record Version(@NotNull @Min(0) Long version) {}
    private final GameService service;
    public GameController(GameService service) { this.service = service; }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GameService.State> create(@Valid @RequestBody Create input, HttpServletRequest request, HttpServletResponse response) {
        var game = service.create(owner(request, response), input.whiteName, input.blackName,
                input.mode == null ? GameMode.LOCAL : input.mode,
                input.humanColor == null ? Color.WHITE : input.humanColor,
                input.difficulty == null ? Difficulty.MEDIUM : input.difficulty);
        return ResponseEntity.created(URI.create("/api/games/" + game.id())).body(game);
    }
    @GetMapping
    public GameService.Page list(@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page, HttpServletRequest request, HttpServletResponse response) {
        return service.list(owner(request, response), page);
    }
    @GetMapping("/{id}")
    public GameService.State get(@PathVariable UUID id, HttpServletRequest request, HttpServletResponse response) {
        return service.get(owner(request, response), id);
    }
    @GetMapping("/{id}/legal-moves")
    public List<String> legal(@PathVariable UUID id, @RequestParam @Pattern(regexp = "[a-h][1-8]") String from, HttpServletRequest request, HttpServletResponse response) {
        return service.legal(owner(request, response), id, from);
    }
    @PostMapping(value = "/{id}/moves", consumes = MediaType.APPLICATION_JSON_VALUE)
    public GameService.State move(@PathVariable UUID id, @Valid @RequestBody Move input, HttpServletRequest request, HttpServletResponse response) {
        return service.move(owner(request, response), id, input.version, new ChessGame.MoveInput(input.from, input.to, input.promotion));
    }
    @PostMapping(value = "/{id}/{action:resignation|draw-agreement|draw-claim}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public GameService.State finish(@PathVariable UUID id, @PathVariable String action, @Valid @RequestBody Version input, HttpServletRequest request, HttpServletResponse response) {
        return service.finish(owner(request, response), id, input.version, action);
    }
    @PostMapping(value = "/{id}/computer-move", consumes = MediaType.APPLICATION_JSON_VALUE)
    public GameService.State computerMove(@PathVariable UUID id, @Valid @RequestBody Version input, HttpServletRequest request, HttpServletResponse response) {
        return service.computerMove(owner(request, response), id, input.version);
    }
    @GetMapping(value = "/{id}/pgn", produces = "application/x-chess-pgn")
    public ResponseEntity<String> pgn(@PathVariable UUID id, HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=chess-" + id + ".pgn")
                .body(service.pgn(owner(request, response), id));
    }

    private UUID owner(HttpServletRequest request, HttpServletResponse response) {
        String value = request.getCookies() == null ? null : Arrays.stream(request.getCookies())
                .filter(c -> c.getName().equals("chess-owner")).map(c -> c.getValue()).findFirst().orElse(null);
        if (value != null) {
            try { return UUID.fromString(value); }
            catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cookie de sessão inválido. Limpe os dados do site."); }
        }
        UUID owner = UUID.randomUUID();
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("chess-owner", owner.toString())
                .httpOnly(true).secure(request.isSecure()).sameSite("Strict").path("/").maxAge(Duration.ofDays(365)).build().toString());
        return owner;
    }
}
