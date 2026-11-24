package dev.chess.infrastructure.persistence;

import jakarta.persistence.*;
import chess.Color;
import dev.chess.domain.GameMode;
import dev.chess.domain.Difficulty;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "games")
public class StoredGame {
    @Id private UUID id;
    @Version private Long version;
    @Column(nullable = false) private UUID ownerId;
    @Column(nullable = false, length = 40) private String whiteName;
    @Column(nullable = false, length = 40) private String blackName;
    @Column(nullable = false, columnDefinition = "text") private String movesJson;
    @Column(nullable = false, length = 7) private String result;
    @Column(nullable = false, length = 40) private String termination;
    @Column(nullable = false) private int moveCount;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private GameMode mode = GameMode.LOCAL;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 5) private Color humanColor = Color.WHITE;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private Difficulty difficulty = Difficulty.MEDIUM;

    protected StoredGame() {}
    public StoredGame(UUID ownerId, String whiteName, String blackName) {
        this.id = UUID.randomUUID(); this.ownerId = ownerId;
        this.whiteName = whiteName; this.blackName = blackName;
        this.movesJson = "[]"; this.result = "*"; this.termination = "IN_PROGRESS";
        this.createdAt = Instant.now(); this.updatedAt = createdAt;
    }
    public void update(String movesJson, String result, String termination, int moveCount) {
        this.movesJson = movesJson; this.result = result; this.termination = termination;
        this.moveCount = moveCount; this.updatedAt = Instant.now();
    }
    public void configure(GameMode mode, Color humanColor, Difficulty difficulty) {
        this.mode = mode; this.humanColor = humanColor; this.difficulty = difficulty;
    }
    public GameMode getMode() { return mode; }
    public Color getHumanColor() { return humanColor; }
    public Difficulty getDifficulty() { return difficulty; }
    public UUID getId() { return id; }
    public Long getVersion() { return version; }
    public String getWhiteName() { return whiteName; }
    public String getBlackName() { return blackName; }
    public String getMovesJson() { return movesJson; }
    public String getResult() { return result; }
    public String getTermination() { return termination; }
    public int getMoveCount() { return moveCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
