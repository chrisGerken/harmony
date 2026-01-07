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

    /**
     * Creates a new board state with the given board and no moves.
     *
     * @param board the board
     */
    public BoardState(Board board) {
        this(board, new ArrayList<>());
    }

    /**
     * Creates a new board state with the given board and move history.
     *
     * @param board the board
     * @param moves the list of moves taken to reach this state
     */
    public BoardState(Board board, List<Move> moves) {
        this.board = board;
        this.moves = new ArrayList<>(moves);
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
     * Gets an unmodifiable list of moves taken to reach this state.
     *
     * @return the move history
     */
    public List<Move> getMoves() {
        return Collections.unmodifiableList(moves);
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
     * Creates a new board state by applying a move to this state.
     *
     * @param move the move to apply
     * @return a new board state with the move applied
     */
    public BoardState applyMove(Move move) {
        Board newBoard = board.swap(move.getRow1(), move.getCol1(),
                                     move.getRow2(), move.getCol2());
        List<Move> newMoves = new ArrayList<>(moves);
        newMoves.add(move);
        return new BoardState(newBoard, newMoves);
    }

    /**
     * Checks if the board in this state is solved.
     *
     * @return true if the board is solved
     */
    public boolean isSolved() {
        return board.isSolved();
    }
}
