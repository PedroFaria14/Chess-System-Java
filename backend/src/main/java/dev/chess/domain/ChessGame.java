package dev.chess.domain;

import chess.*;
import chess_pieces.King;
import chess_pieces.Pawn;
import java.util.ArrayList;
import java.util.List;

/** A local game. Only the Java engine decides whether a move is legal. */
public final class ChessGame {
    public record MoveInput(String from, String to, String promotion) {}
    public record Move(int ply, String from, String to, String promotion, String san, String fen) {}
    public record Square(String square, String type, Color color) {}

    private final ChessMatch match = new ChessMatch();
    private final List<Move> moves = new ArrayList<>();
    private final String initialFen = match.fen();

    public ChessMatch match() { return match; }
    public List<Move> moves() { return List.copyOf(moves); }
    public List<MoveInput> inputs() { return moves.stream().map(m -> new MoveInput(m.from, m.to, m.promotion)).toList(); }
    public String initialFen() { return initialFen; }
    public String uciPosition() {
        if (moves.isEmpty()) return "position startpos";
        return "position startpos moves " + moves.stream().map(m -> m.from + m.to
                + (m.san.contains("=") ? m.promotion.toLowerCase(java.util.Locale.ROOT) : ""))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    public static ChessPosition position(String square) {
        if (square == null || !square.matches("[a-h][1-8]")) throw new ChessException("Casa inválida. Use a1 até h8.");
        return new ChessPosition(square.charAt(0), square.charAt(1) - '0');
    }

    public List<String> legalMoves(String square) {
        if (!match.getResult().equals("*")) return List.of();
        boolean[][] legal = match.possibleMoves(position(square));
        List<String> result = new ArrayList<>();
        for (int row = 0; row < 8; row++) for (int col = 0; col < 8; col++) {
            if (legal[row][col]) result.add("" + (char) ('a' + col) + (8 - row));
        }
        return result;
    }

    public void move(MoveInput input) {
        ChessPosition from = position(input.from), to = position(input.to);
        ChessPiece piece = match.getPieces()[8 - from.getRow()][from.getColumn() - 'a'];
        if (piece == null) throw new ChessException("Não há peça na casa selecionada.");
        boolean castle = piece instanceof King && Math.abs(from.getColumn() - to.getColumn()) == 2;
        boolean capture = match.getPieces()[8 - to.getRow()][to.getColumn() - 'a'] != null
                || piece instanceof Pawn && from.getColumn() != to.getColumn();
        boolean promotion = piece instanceof Pawn && (to.getRow() == 1 || to.getRow() == 8);
        String prefix = piece instanceof Pawn ? (capture ? "" + from.getColumn() : "")
                : piece.toString() + disambiguation(piece, from, to);
        String san = castle ? (to.getColumn() > from.getColumn() ? "O-O" : "O-O-O")
                : prefix + (capture ? "x" : "") + input.to + (promotion ? "=" + input.promotion : "");
        match.performChessMove(from, to, input.promotion);
        san += match.getCheckMate() ? "#" : match.getCheck() ? "+" : "";
        moves.add(new Move(moves.size() + 1, input.from, input.to, input.promotion, san, match.fen()));
    }

    private String disambiguation(ChessPiece piece, ChessPosition from, ChessPosition to) {
        List<ChessPosition> alternatives = new ArrayList<>();
        for (ChessPiece[] row : match.getPieces()) for (ChessPiece other : row) {
            if (other == null || other == piece || other.getClass() != piece.getClass() || other.getColor() != piece.getColor()) continue;
            if (match.possibleMoves(other.getChessPosition())[8 - to.getRow()][to.getColumn() - 'a']) alternatives.add(other.getChessPosition());
        }
        if (alternatives.isEmpty()) return "";
        if (alternatives.stream().noneMatch(p -> p.getColumn() == from.getColumn())) return "" + from.getColumn();
        if (alternatives.stream().noneMatch(p -> p.getRow() == from.getRow())) return "" + from.getRow();
        return from.toString();
    }

    public List<Square> board() {
        List<Square> squares = new ArrayList<>();
        for (ChessPiece[] row : match.getPieces()) for (ChessPiece piece : row) {
            if (piece != null) squares.add(new Square(piece.getChessPosition().toString(), piece.toString(), piece.getColor()));
        }
        return squares;
    }

    public String pgn(String white, String black, String date) {
        return pgn(white, black, date, "Chess Studio • Local");
    }

    public String pgn(String white, String black, String date, String event) {
        StringBuilder pgn = new StringBuilder("[Event \"").append(escape(event)).append("\"]\n[Site \"Local\"]\n[Date \"")
                .append(date).append("\"]\n[Round \"-\"]\n[White \"").append(escape(white))
                .append("\"]\n[Black \"").append(escape(black)).append("\"]\n[Result \"")
                .append(match.getResult()).append("\"]\n\n");
        for (Move move : moves) {
            if (move.ply % 2 == 1) pgn.append((move.ply + 1) / 2).append(". ");
            pgn.append(move.san).append(' ');
        }
        return pgn.append(match.getResult()).append('\n').toString();
    }

    private String escape(String text) { return text.replace("\\", "\\\\").replace("\"", "\\\"").replaceAll("[\\r\\n]", " "); }
}
