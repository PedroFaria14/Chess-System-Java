package dev.chess.domain;

public enum Difficulty {
    EASY(0, 200), MEDIUM(8, 500), HARD(16, 1000);

    private final int skill;
    private final int moveTimeMillis;
    Difficulty(int skill, int moveTimeMillis) { this.skill = skill; this.moveTimeMillis = moveTimeMillis; }
    public int skill() { return skill; }
    public int moveTimeMillis() { return moveTimeMillis; }
}
