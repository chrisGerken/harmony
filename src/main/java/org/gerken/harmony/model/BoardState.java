package org.gerken.harmony.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a board state in the search space.
 * Includes the current board configuration and the sequence of moves taken to reach it.
 */
public class BoardState {

    private final Board board;
    private final List<Move> moves;
    private final int remainingMoves;

    /**
     * Creates a new board state with the given board and no moves.
     * Calculates remaining moves by summing all tiles' remaining moves divided by 2.
     *
     * @param board the board
     */
    public BoardState(Board board) {
        this(board, new ArrayList<>());
    }

    /**
     * Creates a new board state with the given board and move history.
     * Calculates remaining moves by summing all tiles' remaining moves divided by 2.
     *
     * @param board the board
     * @param moves the list of moves taken to reach this state
     */
    public BoardState(Board board, List<Move> moves) {
        this.board = board;
        this.moves = new ArrayList<>(moves);
        this.remainingMoves = calculateRemainingMoves(board);
    }

    /**
     * Private constructor for creating successor states with pre-calculated remaining moves.
     * Used by applyMove() to avoid recalculating on every state transition.
     *
     * @param board the board
     * @param moves the list of moves taken to reach this state
     * @param remainingMoves the pre-calculated remaining moves count
     */
    private BoardState(Board board, List<Move> moves, int remainingMoves) {
        this.board = board;
        this.moves = new ArrayList<>(moves);
        this.remainingMoves = remainingMoves;
    }

    /**
     * Gets the board for this state.
     *
     * @return the board
     */
    public Board getBoard() {
        return board;
    }

    /**
     * Gets the list of moves taken to reach this state.
     *
     * @return the move history
     */
    public List<Move> getMoves() {
        return moves;
    }

    /**
     * Gets the number of moves taken to reach this state.
     *
     * @return the move count
     */
    public int getMoveCount() {
        return moves.size();
    }

    /**
     * Gets the cached remaining moves count.
     * This is the sum of all tiles' remaining moves divided by 2.
     *
     * @return the remaining moves count
     */
    public int getRemainingMoves() {
        return remainingMoves;
    }

    /**
     * Creates a new board state by applying a move to this state.
     * Decrements the remaining moves count by 1 (each move reduces two tiles by 1 each).
     *
     * @param move the move to apply
     * @return a new board state with the move applied
     */
    public BoardState applyMove(Move move) {
        Board newBoard = board.swap(move.getRow1(), move.getCol1(),
                                     move.getRow2(), move.getCol2());
        List<Move> newMoves = new ArrayList<>(moves);
        newMoves.add(move);
        // Each move decrements two tiles by 1 each, so remaining moves decreases by 1
        return new BoardState(newBoard, newMoves, remainingMoves - 1);
    }

    /**
     * Checks if the board in this state is solved.
     *
     * @return true if the board is solved
     */
    public boolean isSolved() {
        return board.isSolved();
    }

    /**
     * Calculates the remaining moves for a board by summing all tiles' remaining moves
     * and dividing by 2 (since each move involves two tiles).
     *
     * @param board the board to calculate for
     * @return the remaining moves count
     */
    private int calculateRemainingMoves(Board board) {
        int totalRemainingMoves = 0;
        for (int row = 0; row < board.getRowCount(); row++) {
            for (int col = 0; col < board.getColumnCount(); col++) {
                Tile tile = board.getTile(row, col);
                totalRemainingMoves += tile.getRemainingMoves();
            }
        }
        return totalRemainingMoves / 2;
    }
}
