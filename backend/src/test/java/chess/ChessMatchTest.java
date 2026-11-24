package chess;

import boardgame.Board;
import boardgame.Piece;
import boardgame.Position;
import chess_pieces.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ChessMatchTest {
    private ChessPosition at(String value) { return new ChessPosition(value.charAt(0), value.charAt(1) - '0'); }
    private void move(ChessMatch game, String from, String to) { game.performChessMove(at(from), at(to)); }

    @Test void initialPositionHasTwentyLegalMovesAndFourHundredReplies() {
        ChessMatch game = new ChessMatch();
        List<String[]> moves = moves(game);
        assertThat(moves).hasSize(20);
        int replies = 0;
        for (String[] move : moves) {
            ChessMatch child = new ChessMatch();
            move(child, move[0], move[1]);
            replies += moves(child).size();
        }
        assertThat(replies).isEqualTo(400);
        assertThat(game.fen()).isEqualTo("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
    }

    @Test void foolsMateEndsGameAndRejectsFurtherMoves() {
        ChessMatch game = new ChessMatch();
        move(game, "f2", "f3"); move(game, "e7", "e5");
        move(game, "g2", "g4"); move(game, "d8", "h4");
        assertThat(game.getResult()).isEqualTo("0-1");
        assertThat(game.getTermination()).isEqualTo("CHECKMATE");
        assertThatThrownBy(() -> move(game, "a2", "a3")).isInstanceOf(ChessException.class);
    }

    @Test void initialPositionPerftDepthThreeIs8902() {
        int positions = 0;
        for (String[] first : moves(new ChessMatch())) {
            ChessMatch next = new ChessMatch(); move(next, first[0], first[1]);
            for (String[] second : moves(next)) {
                ChessMatch game = new ChessMatch();
                move(game, first[0], first[1]); move(game, second[0], second[1]);
                positions += moves(game).size();
            }
        }
        assertThat(positions).isEqualTo(8902);
    }

    @Test void enPassantCannotExposeOwnKingAndSimulationIsReversible() throws Exception {
        ChessMatch game = position("e1:K", "e5:P", "a8:k", "e8:r", "d5:p");
        set(game, "enPassantVulnerable", game.getPieces()[3][3]);
        String before = game.fen();
        assertThat(game.possibleMoves(at("e5"))[2][3]).isFalse();
        assertThat(game.fen()).isEqualTo(before);
        assertThat(game.getCapturedPieces()).isEmpty();
    }

    @Test void fiftyMovesRequireClaimButSeventyFiveAreAutomatic() throws Exception {
        ChessMatch claim = position("e1:K", "h1:R", "a8:k");
        set(claim, "halfmoveClock", 99);
        move(claim, "h1", "h2");
        assertThat(claim.canClaimDraw()).isTrue();
        assertThat(claim.getResult()).isEqualTo("*");
        claim.claimDraw();
        assertThat(claim.getTermination()).isEqualTo("FIFTY_MOVES");
        ChessMatch automatic = position("e1:K", "h1:R", "a8:k");
        set(automatic, "halfmoveClock", 149);
        move(automatic, "h1", "h2");
        assertThat(automatic.getTermination()).isEqualTo("SEVENTY_FIVE_MOVES");
    }

    @Test void queenPromotionCanDeliverMateImmediately() throws Exception {
        ChessMatch game = position("c7:K", "b7:P", "a8:k");
        game.performChessMove(at("b7"), at("b8"), "Q");
        assertThat(game.getTermination()).isEqualTo("CHECKMATE");
        assertThat(game.getResult()).isEqualTo("1-0");
    }

    private void set(ChessMatch game, String field, Object value) throws Exception {
        Field target = ChessMatch.class.getDeclaredField(field); target.setAccessible(true); target.set(game, value);
    }

    @Test void enPassantCaptureAndMoveQueriesPreserveState() {
        ChessMatch game = new ChessMatch();
        move(game, "e2", "e4"); move(game, "a7", "a6");
        move(game, "e4", "e5"); move(game, "d7", "d5");
        String before = game.fen();
        assertThat(game.possibleMoves(at("e5"))[2][3]).isTrue();
        assertThat(game.fen()).isEqualTo(before);
        move(game, "e5", "d6");
        assertThat(game.getPieces()[3][3]).isNull();
        assertThat(game.getCapturedPieces()).hasSize(1);
    }

    @Test void threefoldRequiresClaimButFivefoldIsAutomatic() {
        ChessMatch game = new ChessMatch();
        cycle(game); cycle(game);
        assertThat(game.canClaimDraw()).isTrue();
        assertThat(game.getResult()).isEqualTo("*");
        cycle(game); cycle(game);
        assertThat(game.getTermination()).isEqualTo("FIVEFOLD_REPETITION");
    }

    @Test void castlingThroughAttackIsIllegal() throws Exception {
        ChessMatch game = position("e1:K", "h1:R", "a8:k", "f8:r");
        assertThat(game.possibleMoves(at("e1"))[7][6]).isFalse();
        assertThatThrownBy(() -> move(game, "e1", "g1")).isInstanceOf(ChessException.class);
    }

    @Test void legalCastlingMovesRookAndKing() throws Exception {
        ChessMatch game = position("e1:K", "h1:R", "a8:k");
        move(game, "e1", "g1");
        assertThat(game.getPieces()[7][5]).isInstanceOf(Rook.class);
        assertThat(game.getPieces()[7][6]).isInstanceOf(King.class);
    }

    @Test void pinnedPieceCannotExposeKing() throws Exception {
        ChessMatch game = position("e1:K", "e2:R", "a8:k", "e8:r");
        assertThat(game.possibleMoves(at("e2"))[6][3]).isFalse();
    }

    @Test void underpromotionIsAppliedBeforeResultCalculation() throws Exception {
        ChessMatch game = position("a1:K", "c7:P", "h8:k");
        game.performChessMove(at("c7"), at("c8"), "N");
        assertThat(game.getPieces()[0][2]).isInstanceOf(Knight.class);
        assertThat(game.getTermination()).isEqualTo("INSUFFICIENT_MATERIAL");
    }

    @Test void stalemateIsNotCheckmate() throws Exception {
        ChessMatch game = position("c6:K", "b6:Q", "a8:k");
        move(game, "b6", "c7");
        assertThat(game.getTermination()).isEqualTo("STALEMATE");
        assertThat(game.getCheckMate()).isFalse();
    }

    private void cycle(ChessMatch game) {
        move(game, "g1", "f3"); move(game, "g8", "f6");
        move(game, "f3", "g1"); move(game, "f6", "g8");
    }

    private List<String[]> moves(ChessMatch game) {
        List<String[]> result = new ArrayList<>();
        for (ChessPiece[] row : game.getPieces()) for (ChessPiece piece : row) {
            if (piece == null || piece.getColor() != game.getCurrentPlayer()) continue;
            boolean[][] legal = game.possibleMoves(piece.getChessPosition());
            for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) {
                if (legal[r][c]) result.add(new String[]{piece.getChessPosition().toString(), "" + (char)('a' + c) + (8-r)});
            }
        }
        return result;
    }

    // A fixture builds minimal positions without adding test-only mutation to the production API.
    @SuppressWarnings("unchecked")
    private ChessMatch position(String... placements) throws Exception {
        ChessMatch game = new ChessMatch();
        Field boardField = ChessMatch.class.getDeclaredField("board"); boardField.setAccessible(true);
        Board board = (Board) boardField.get(game);
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) board.removePiece(new Position(r, c));
        Field listField = ChessMatch.class.getDeclaredField("piecesOnTheBoard"); listField.setAccessible(true);
        List<Piece> pieces = (List<Piece>) listField.get(game); pieces.clear();
        for (String placement : placements) {
            char symbol = placement.charAt(3);
            Color color = Character.isUpperCase(symbol) ? Color.WHITE : Color.BLACK;
            ChessPiece piece = switch (Character.toUpperCase(symbol)) {
                case 'K' -> new King(board, color, game);
                case 'Q' -> new Queen(board, color);
                case 'R' -> new Rook(board, color);
                case 'B' -> new Bishop(board, color);
                case 'N' -> new Knight(board, color);
                default -> new Pawn(board, color, game);
            };
            board.placePiece(piece, at(placement.substring(0, 2)).toPosition()); pieces.add(piece);
        }
        return game;
    }
}
