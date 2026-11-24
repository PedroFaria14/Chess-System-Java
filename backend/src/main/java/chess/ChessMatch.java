package chess;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

import boardgame.Board;
import boardgame.Piece;
import boardgame.Position;
import chess_pieces.Bishop;
import chess_pieces.King;
import chess_pieces.Knight;
import chess_pieces.Pawn;
import chess_pieces.Queen;
import chess_pieces.Rook;

public class ChessMatch {

    private int turn;
    private Color currentPlayer;
    private Board board;
    private boolean check;
    private boolean checkMate;
    private ChessPiece enPassantVulnerable;
    private ChessPiece promoted;
    private int halfmoveClock;
    private int plyCount;
    private String result = "*";
    private String termination = "IN_PROGRESS";
    private final Map<String, Integer> repetitions = new HashMap<>();

    private List<Piece> piecesOnTheBoard = new ArrayList<>();
    private List<Piece> capturedPieces = new ArrayList<>();

    public ChessMatch() {
        board = new Board(8, 8);
        turn = 1;
        currentPlayer = Color.WHITE;
        initialSetup();
        repetitions.put(repetitionKey(), 1);
    }

    public int getTurn() {
        return turn;
    }

    public Color getCurrentPlayer() {
        return currentPlayer;
    }

    public boolean getCheck() {
        return check;
    }

    public boolean getCheckMate() {
        return checkMate;
    }

    public ChessPiece getEnPassantVulnerable() {
        return enPassantVulnerable;
    }

    public ChessPiece getPromoted() {
        return promoted;
    }

    public ChessPiece[][] getPieces() {
        ChessPiece[][] mat = new ChessPiece[board.getRows()][board.getColumns()];
        for (int i = 0; i < board.getRows(); i++) {
            for (int j = 0; j < board.getColumns(); j++) {
                mat[i][j] = (ChessPiece) board.piece(i, j);
            }
        }
        return mat;
    }

    public boolean[][] possibleMoves(ChessPosition sourcePosition) {
        Position position = sourcePosition.toPosition();
        validateSourcePosition(position);
        return legalMoves((ChessPiece) board.piece(position));
    }

    private boolean[][] legalMoves(ChessPiece piece) {
        Position source = piece.getChessPosition().toPosition();
        boolean[][] moves = piece.possibleMoves();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (!moves[row][col]) continue;
                Position target = new Position(row, col);
                if (board.piece(target) instanceof King) {
                    moves[row][col] = false;
                    continue;
                }
                Piece captured = makeMove(source, target);
                moves[row][col] = !testCheck(piece.getColor());
                undoMove(source, target, captured);
            }
        }
        return moves;
    }

    public ChessPiece performChessMove(ChessPosition sourcePosition, ChessPosition targetPosition) {
        return performChessMove(sourcePosition, targetPosition, "Q");
    }

    public ChessPiece performChessMove(ChessPosition sourcePosition, ChessPosition targetPosition, String promotion) {
        if (!result.equals("*")) throw new ChessException("A partida já terminou.");
        if (promotion == null || !List.of("Q", "R", "B", "N").contains(promotion)) {
            throw new ChessException("Escolha dama, torre, bispo ou cavalo para a promoção.");
        }
        Position source = sourcePosition.toPosition();
        Position target = targetPosition.toPosition();
        validateSourcePosition(source);
        validateTargetPosition(source, target);
        Piece capturedPiece = makeMove(source, target);

        if (testCheck(currentPlayer)) {
            undoMove(source, target, capturedPiece);
            throw new ChessException("You can't put yourself in check");
        }

        ChessPiece movedPiece = (ChessPiece) board.piece(target);

        // #specialmove promotion
        promoted = null;
        if (movedPiece instanceof Pawn) {
            if ((movedPiece.getColor() == Color.WHITE && target.getRow() == 0)
                    || (movedPiece.getColor() == Color.BLACK && target.getRow() == 7)) {
                promoted = (ChessPiece) board.piece(target);
                promoted = replacePromotedPiece(promotion);
            }
        }

        // #specialmove en passant
        if (movedPiece instanceof Pawn
                && (target.getRow() == source.getRow() - 2 || target.getRow() == source.getRow() + 2)) {
            enPassantVulnerable = movedPiece;
        } else {
            enPassantVulnerable = null;
        }

        halfmoveClock = movedPiece instanceof Pawn || capturedPiece != null ? 0 : halfmoveClock + 1;
        plyCount++;
        nextTurn();
        check = testCheck(currentPlayer);
        boolean hasMove = hasLegalMove(currentPlayer);
        if (!hasMove) {
            checkMate = check;
            finish(check ? (currentPlayer == Color.WHITE ? "0-1" : "1-0") : "1/2-1/2",
                    check ? "CHECKMATE" : "STALEMATE");
        } else if (insufficientMaterial()) {
            finish("1/2-1/2", "INSUFFICIENT_MATERIAL");
        }
        int count = repetitions.merge(repetitionKey(), 1, Integer::sum);
        if (result.equals("*") && count >= 5) finish("1/2-1/2", "FIVEFOLD_REPETITION");
        if (result.equals("*") && halfmoveClock >= 150) finish("1/2-1/2", "SEVENTY_FIVE_MOVES");

        return (ChessPiece) capturedPiece;
    }

    private ChessPiece replacePromotedPiece(String type) {
        if (promoted == null) {
            throw new IllegalStateException("There is no piece to be promoted");
        }
        if (!List.of("B", "N", "R", "Q").contains(type)) throw new ChessException("Promoção inválida.");

        Position pos = promoted.getChessPosition().toPosition();
        Piece p = board.removePiece(pos);
        piecesOnTheBoard.remove(p);

        ChessPiece newPiece = newPiece(type, promoted.getColor());
        board.placePiece(newPiece, pos);
        piecesOnTheBoard.add(newPiece);

        return newPiece;
    }

    private ChessPiece newPiece(String type, Color color) {
        if (type.equals("B"))
            return new Bishop(board, color);
        if (type.equals("N"))
            return new Knight(board, color);
        if (type.equals("Q"))
            return new Queen(board, color);
        return new Rook(board, color);
    }

    private Piece makeMove(Position source, Position target) {
        ChessPiece p = (ChessPiece) board.removePiece(source);
        p.increaseMoveCount();
        Piece capturedPiece = board.removePiece(target);
        board.placePiece(p, target);

        if (capturedPiece != null) {
            piecesOnTheBoard.remove(capturedPiece);
            capturedPieces.add(capturedPiece);
        }

        // #specialmove castling kingside rook
        if (p instanceof King && target.getColumn() == source.getColumn() + 2) {
            Position sourceT = new Position(source.getRow(), source.getColumn() + 3);
            Position targetT = new Position(source.getRow(), source.getColumn() + 1);
            ChessPiece rook = (ChessPiece) board.removePiece(sourceT);
            board.placePiece(rook, targetT);
            rook.increaseMoveCount();
        }

        // #specialmove castling queenside rook
        if (p instanceof King && target.getColumn() == source.getColumn() - 2) {
            Position sourceT = new Position(source.getRow(), source.getColumn() - 4);
            Position targetT = new Position(source.getRow(), source.getColumn() - 1);
            ChessPiece rook = (ChessPiece) board.removePiece(sourceT);
            board.placePiece(rook, targetT);
            rook.increaseMoveCount();
        }

        // #specialmove en passant
        if (p instanceof Pawn) {
            if (source.getColumn() != target.getColumn() && capturedPiece == null) {
                Position pawnPosition;
                if (p.getColor() == Color.WHITE) {
                    pawnPosition = new Position(target.getRow() + 1, target.getColumn());
                } else {
                    pawnPosition = new Position(target.getRow() - 1, target.getColumn());
                }
                capturedPiece = board.removePiece(pawnPosition);
                capturedPieces.add(capturedPiece);
                piecesOnTheBoard.remove(capturedPiece);
            }
        }

        return capturedPiece;
    }

    private void undoMove(Position source, Position target, Piece capturedPiece) {
        ChessPiece p = (ChessPiece) board.removePiece(target);
        p.decreaseMoveCount();
        board.placePiece(p, source);

        if (capturedPiece != null) {
            board.placePiece(capturedPiece, target);
            capturedPieces.remove(capturedPiece);
            piecesOnTheBoard.add(capturedPiece);
        }

        // #specialmove castling kingside rook
        if (p instanceof King && target.getColumn() == source.getColumn() + 2) {
            Position sourceT = new Position(source.getRow(), source.getColumn() + 3);
            Position targetT = new Position(source.getRow(), source.getColumn() + 1);
            ChessPiece rook = (ChessPiece) board.removePiece(targetT);
            board.placePiece(rook, sourceT);
            rook.decreaseMoveCount();
        }

        // #specialmove castling queenside rook
        if (p instanceof King && target.getColumn() == source.getColumn() - 2) {
            Position sourceT = new Position(source.getRow(), source.getColumn() - 4);
            Position targetT = new Position(source.getRow(), source.getColumn() - 1);
            ChessPiece rook = (ChessPiece) board.removePiece(targetT);
            board.placePiece(rook, sourceT);
            rook.decreaseMoveCount();
        }

        // #specialmove en passant
        if (p instanceof Pawn) {
            if (source.getColumn() != target.getColumn() && capturedPiece == enPassantVulnerable) {
                ChessPiece pawn = (ChessPiece) board.removePiece(target);
                Position pawnPosition;
                if (p.getColor() == Color.WHITE) {
                    pawnPosition = new Position(3, target.getColumn());
                } else {
                    pawnPosition = new Position(4, target.getColumn());
                }
                board.placePiece(pawn, pawnPosition);
            }
        }
    }

    private void validateSourcePosition(Position position) {
        if (!board.thereIsAPiece(position)) {
            throw new ChessException("Não há peça na casa selecionada.");
        }
        if (currentPlayer != ((ChessPiece) board.piece(position)).getColor()) {
            throw new ChessException("Selecione uma peça do jogador da vez.");
        }
    }

    private void validateTargetPosition(Position source, Position target) {
        if (!legalMoves((ChessPiece) board.piece(source))[target.getRow()][target.getColumn()]) {
            throw new ChessException("Movimento ilegal. O rei deve permanecer protegido.");
        }
    }

    private void nextTurn() {
        turn++;
        currentPlayer = (currentPlayer == Color.WHITE) ? Color.BLACK : Color.WHITE;
    }

    private Color opponent(Color color) {
        return (color == Color.WHITE) ? Color.BLACK : Color.WHITE;
    }

    private ChessPiece king(Color color) {
        List<Piece> list = piecesOnTheBoard.stream().filter(x -> ((ChessPiece) x).getColor() == color)
                .collect(Collectors.toList());
        for (Piece p : list) {
            if (p instanceof King) {
                return (ChessPiece) p;
            }
        }
        throw new IllegalStateException("There is no " + color + " king on the board");
    }

    private boolean testCheck(Color color) {
        Position kingPosition = king(color).getChessPosition().toPosition();
        return isAttacked(kingPosition, opponent(color));
    }

    // Attack maps differ from movement maps: pawns attack diagonals and kings never castle here.
    private boolean isAttacked(Position target, Color attacker) {
        for (Piece raw : piecesOnTheBoard) {
            ChessPiece piece = (ChessPiece) raw;
            if (piece.getColor() != attacker) continue;
            Position from = piece.getChessPosition().toPosition();
            int dr = target.getRow() - from.getRow();
            int dc = target.getColumn() - from.getColumn();
            if (dr == 0 && dc == 0) continue;
            if (piece instanceof Pawn) {
                if (dr == (attacker == Color.WHITE ? -1 : 1) && Math.abs(dc) == 1) return true;
                continue;
            }
            if (piece instanceof Knight) {
                if (Math.abs(dr) * Math.abs(dc) == 2) return true;
                continue;
            }
            if (piece instanceof King) {
                if (Math.max(Math.abs(dr), Math.abs(dc)) == 1) return true;
                continue;
            }
            boolean diagonal = Math.abs(dr) == Math.abs(dc);
            boolean straight = dr == 0 || dc == 0;
            if (!(piece instanceof Queen && (diagonal || straight)
                    || piece instanceof Bishop && diagonal || piece instanceof Rook && straight)) continue;
            int r = from.getRow() + Integer.signum(dr);
            int c = from.getColumn() + Integer.signum(dc);
            boolean clear = true;
            while (r != target.getRow() || c != target.getColumn()) {
                if (board.piece(r, c) != null) { clear = false; break; }
                r += Integer.signum(dr); c += Integer.signum(dc);
            }
            if (clear) return true;
        }
        return false;
    }

    public boolean canCastle(Color color, boolean kingSide) {
        int row = color == Color.WHITE ? 7 : 0;
        ChessPiece king = (ChessPiece) board.piece(row, 4);
        ChessPiece rook = (ChessPiece) board.piece(row, kingSide ? 7 : 0);
        if (!(king instanceof King) || king.getColor() != color || king.getMoveCount() != 0
                || !(rook instanceof Rook) || rook.getColor() != color || rook.getMoveCount() != 0) return false;
        for (int col = kingSide ? 5 : 1; col <= (kingSide ? 6 : 3); col++) {
            if (board.piece(row, col) != null) return false;
        }
        if (testCheck(color)) return false;
        Position source = new Position(row, 4);
        Position transit = new Position(row, kingSide ? 5 : 3);
        board.removePiece(source);
        board.placePiece(king, transit);
        boolean safe = !isAttacked(transit, opponent(color));
        board.removePiece(transit);
        board.placePiece(king, source);
        return safe;
    }

    private boolean hasLegalMove(Color color) {
        for (Piece raw : new ArrayList<>(piecesOnTheBoard)) {
            ChessPiece piece = (ChessPiece) raw;
            if (piece.getColor() != color) continue;
            for (boolean[] row : legalMoves(piece)) for (boolean move : row) if (move) return true;
        }
        return false;
    }

    private boolean insufficientMaterial() {
        List<ChessPiece> minor = piecesOnTheBoard.stream().map(p -> (ChessPiece) p)
                .filter(p -> !(p instanceof King)).toList();
        if (minor.isEmpty()) return true;
        if (minor.size() == 1) return minor.getFirst() instanceof Bishop || minor.getFirst() instanceof Knight;
        if (minor.stream().allMatch(p -> p instanceof Bishop)) {
            return minor.stream().map(p -> p.getChessPosition().toPosition())
                    .map(p -> (p.getRow() + p.getColumn()) % 2).distinct().count() == 1;
        }
        return false;
    }

    public String getResult() { return result; }
    public String getTermination() { return termination; }
    public boolean canClaimDraw() {
        return result.equals("*") && (halfmoveClock >= 100 || repetitions.getOrDefault(repetitionKey(), 0) >= 3);
    }
    public void claimDraw() {
        if (!canClaimDraw()) throw new ChessException("Ainda não é possível reivindicar empate.");
        finish("1/2-1/2", halfmoveClock >= 100 ? "FIFTY_MOVES" : "THREEFOLD_REPETITION");
    }
    public void finish(String outcome, String reason) {
        if (!result.equals("*")) throw new ChessException("A partida já terminou.");
        result = outcome; termination = reason;
    }
    public List<ChessPiece> getCapturedPieces() {
        return capturedPieces.stream().map(p -> (ChessPiece) p).toList();
    }

    public String fen() {
        StringBuilder value = new StringBuilder();
        for (int row = 0; row < 8; row++) {
            int empty = 0;
            for (int col = 0; col < 8; col++) {
                ChessPiece p = (ChessPiece) board.piece(row, col);
                if (p == null) { empty++; continue; }
                if (empty > 0) { value.append(empty); empty = 0; }
                value.append(p.getColor() == Color.WHITE ? p.toString() : p.toString().toLowerCase());
            }
            if (empty > 0) value.append(empty);
            if (row < 7) value.append('/');
        }
        String rights = "";
        if (castlingRight(Color.WHITE, 7)) rights += "K";
        if (castlingRight(Color.WHITE, 0)) rights += "Q";
        if (castlingRight(Color.BLACK, 7)) rights += "k";
        if (castlingRight(Color.BLACK, 0)) rights += "q";
        String ep = "-";
        if (enPassantVulnerable != null) {
            ChessPosition p = enPassantVulnerable.getChessPosition();
            ep = "" + p.getColumn() + (p.getRow() == 4 ? 3 : 6);
        }
        return value + " " + (currentPlayer == Color.WHITE ? "w" : "b") + " "
                + (rights.isEmpty() ? "-" : rights) + " " + ep + " " + halfmoveClock + " " + (plyCount / 2 + 1);
    }

    private boolean castlingRight(Color color, int column) {
        int row = color == Color.WHITE ? 7 : 0;
        return board.piece(row, 4) instanceof King k && k.getColor() == color && k.getMoveCount() == 0
                && board.piece(row, column) instanceof Rook r && r.getColor() == color && r.getMoveCount() == 0;
    }

    private String repetitionKey() {
        String[] fields = fen().split(" ");
        boolean legalEnPassant = false;
        if (enPassantVulnerable != null) {
            ChessPosition ep = enPassantVulnerable.getChessPosition();
            for (Piece raw : new ArrayList<>(piecesOnTheBoard)) {
                ChessPiece p = (ChessPiece) raw;
                if (!(p instanceof Pawn) || p.getColor() != currentPlayer) continue;
                ChessPosition from = p.getChessPosition();
                if (from.getRow() == ep.getRow() && Math.abs(from.getColumn() - ep.getColumn()) == 1) {
                    int row = 8 - ep.getRow() + (currentPlayer == Color.WHITE ? -1 : 1);
                    if (legalMoves(p)[row][ep.getColumn() - 'a']) legalEnPassant = true;
                }
            }
        }
        return fields[0] + " " + fields[1] + " " + fields[2] + " " + (legalEnPassant ? fields[3] : "-");
    }

    private void placeNewPiece(char column, int row, ChessPiece piece) {
        board.placePiece(piece, new ChessPosition(column, row).toPosition());
        piecesOnTheBoard.add(piece);
    }

    private void initialSetup() {
        placeNewPiece('a', 1, new Rook(board, Color.WHITE));
        placeNewPiece('b', 1, new Knight(board, Color.WHITE));
        placeNewPiece('c', 1, new Bishop(board, Color.WHITE));
        placeNewPiece('d', 1, new Queen(board, Color.WHITE));
        placeNewPiece('e', 1, new King(board, Color.WHITE, this));
        placeNewPiece('f', 1, new Bishop(board, Color.WHITE));
        placeNewPiece('g', 1, new Knight(board, Color.WHITE));
        placeNewPiece('h', 1, new Rook(board, Color.WHITE));
        placeNewPiece('a', 2, new Pawn(board, Color.WHITE, this));
        placeNewPiece('b', 2, new Pawn(board, Color.WHITE, this));
        placeNewPiece('c', 2, new Pawn(board, Color.WHITE, this));
        placeNewPiece('d', 2, new Pawn(board, Color.WHITE, this));
        placeNewPiece('e', 2, new Pawn(board, Color.WHITE, this));
        placeNewPiece('f', 2, new Pawn(board, Color.WHITE, this));
        placeNewPiece('g', 2, new Pawn(board, Color.WHITE, this));
        placeNewPiece('h', 2, new Pawn(board, Color.WHITE, this));

        placeNewPiece('a', 8, new Rook(board, Color.BLACK));
        placeNewPiece('b', 8, new Knight(board, Color.BLACK));
        placeNewPiece('c', 8, new Bishop(board, Color.BLACK));
        placeNewPiece('d', 8, new Queen(board, Color.BLACK));
        placeNewPiece('e', 8, new King(board, Color.BLACK, this));
        placeNewPiece('f', 8, new Bishop(board, Color.BLACK));
        placeNewPiece('g', 8, new Knight(board, Color.BLACK));
        placeNewPiece('h', 8, new Rook(board, Color.BLACK));
        placeNewPiece('a', 7, new Pawn(board, Color.BLACK, this));
        placeNewPiece('b', 7, new Pawn(board, Color.BLACK, this));
        placeNewPiece('c', 7, new Pawn(board, Color.BLACK, this));
        placeNewPiece('d', 7, new Pawn(board, Color.BLACK, this));
        placeNewPiece('e', 7, new Pawn(board, Color.BLACK, this));
        placeNewPiece('f', 7, new Pawn(board, Color.BLACK, this));
        placeNewPiece('g', 7, new Pawn(board, Color.BLACK, this));
        placeNewPiece('h', 7, new Pawn(board, Color.BLACK, this));
    }
}
