package org.gerken.harmony.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a board state in the search space.
 * Includes the current board configuration and links to the previous state.
 */
public class BoardState {

    private final Board board;
    private final Move lastMove;
    private final int remainingMoves;
    private final int bestScore;
    private BoardState previousBoardState = null;
    private boolean first = false;

    /**
     * Creates a new board state with the given board and no moves.
     * Calculates remaining moves by summing all tiles' remaining moves divided by 2.
     *
     * @param board the board
     */
    public BoardState(Board board) {
        this(board, (Move) null);
    }

    /**
     * Creates a new board state with the given board and last move.
     * Calculates remaining moves by summing all tiles' remaining moves divided by 2.
     * Sets bestScore to the board's score (since there's no parent).
     *
     * @param board the board
     * @param lastMove the last move taken to reach this state
     */
    public BoardState(Board board, Move lastMove) {
        this.board = board;
        this.lastMove = lastMove;
        this.remainingMoves = calculateRemainingMoves(board);
        this.bestScore = board.getScore();
    }

    /**
     * Private constructor for creating successor states with pre-calculated remaining moves and bestScore.
     * Used by applyMove() to avoid recalculating on every state transition.
     *
     * @param board the board
     * @param lastMove the last move taken to reach this state
     * @param remainingMoves the pre-calculated remaining moves count
     * @param bestScore the minimum score seen along the path from initial state
     */
    private BoardState(Board board, Move lastMove, int remainingMoves, int bestScore) {
        this.board = board;
        this.lastMove = lastMove;
        this.remainingMoves = remainingMoves;
        this.bestScore = bestScore;
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
     * Gets the last move taken to reach this state.
     *
     * @return the last move, or null if this is the initial state
     */
    public Move getLastMove() {
        return lastMove;
    }

    /**
     * Gets the previous board state.
     *
     * @return the previous board state, or null if this is the initial state
     */
    public BoardState getPreviousBoardState() {
        return previousBoardState;
    }

    /**
     * Sets the previous board state.
     *
     * @param previousBoardState the previous board state
     */
    public void setPreviousBoardState(BoardState previousBoardState) {
        this.previousBoardState = previousBoardState;
    }

    /**
     * Gets whether this is a "first" state (on the primary search path from initial state).
     *
     * @return true if this is a first state
     */
    public boolean isFirst() {
        return first;
    }

    /**
     * Sets whether this is a "first" state.
     *
     * @param first true if this is a first state
     */
    public void setFirst(boolean first) {
        this.first = first;
    }

    /**
     * Gets the number of moves taken to reach this state.
     *
     * @return the move count
     */
    public int getMoveCount() {
        int count = 0;
        BoardState current = this;
        while (current != null && current.lastMove != null) {
            count++;
            current = current.previousBoardState;
        }
        return count;
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
     * Gets the best (lowest) score seen along the path from the initial state to this state.
     * This is effectively the minimum of this board's score and the parent's bestScore.
     *
     * @return the best score seen along the path
     */
    public int getBestScore() {
        return bestScore;
    }

    /**
     * Creates a new board state by applying a move to this state.
     * Decrements the remaining moves count by 1 (each move reduces two tiles by 1 each).
     * The new state's first flag is set to false.
     * The new state's bestScore is the minimum of the new board's score and this state's bestScore.
     *
     * @param move the move to apply
     * @return a new board state with the move applied
     */
    public BoardState applyMove(Move move) {
        Board newBoard = board.swap(move.getRow1(), move.getCol1(),
                                     move.getRow2(), move.getCol2());
        // Each move decrements two tiles by 1 each, so remaining moves decreases by 1
        // bestScore is the minimum of the new board's score and the parent's bestScore
        int newBestScore = Math.min(newBoard.getScore(), this.bestScore);
        BoardState newState = new BoardState(newBoard, move, remainingMoves - 1, newBestScore);
        newState.setPreviousBoardState(this);
        // Propagate first flag only if this state is first AND there's exactly one possible move
        return newState;
    }

    /**
     * Checks if the board in this state is solved.
     * First checks if remaining moves is zero (quick check), then verifies board state.
     *
     * @return true if the board is solved
     */
    public boolean isSolved() {
        if (remainingMoves != 0) {
            return false;
        }
        return board.isSolved();
    }

    /**
     * Gets the complete move history from the initial state to this state.
     * Traverses the previousBoardState chain to build the list in order.
     *
     * @return list of moves in order from first to last
     */
    public List<Move> getMoveHistory() {
        List<Move> history = new ArrayList<>();
        BoardState current = this;
        while (current != null && current.lastMove != null) {
            history.add(0, current.lastMove);
            current = current.previousBoardState;
        }
        return history;
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

	@Override
	public String toString() {
		return board.toString() + "\nRemaining moves: " + remainingMoves + "\nBest score: " + bestScore;
	}
}
