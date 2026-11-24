package dev.chess.infrastructure.web;

import dev.chess.application.ChessEngine;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/engine")
public class EngineController {
    private final ChessEngine engine;
    public EngineController(ChessEngine engine) { this.engine = engine; }
    @GetMapping public ChessEngine.Status status() { return engine.status(); }
}
