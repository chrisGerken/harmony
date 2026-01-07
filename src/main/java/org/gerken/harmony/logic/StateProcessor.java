package org.gerken.harmony.logic;

import org.gerken.harmony.invalidity.InvalidityTestCoordinator;
import org.gerken.harmony.model.Board;
import org.gerken.harmony.model.BoardState;
import org.gerken.harmony.model.Move;
import org.gerken.harmony.model.Tile;

import java.util.ArrayList;
import java.util.List;

/**
 * Worker thread that processes board states from the queue.
 * Generates successor states, checks validity, and searches for solutions.
 */
public class StateProcessor implements Runnable {

    private final PendingStates pendingStates;
    private final InvalidityTestCoordinator coordinator;
    private final long pollTimeoutMs;

    /**
     * Creates a new state processor.
     *
     * @param pendingStates the shared container of pending states and statistics
     */
    public StateProcessor(PendingStates pendingStates) {
        this.pendingStates = pendingStates;
        this.coordinator = InvalidityTestCoordinator.getInstance();
        this.pollTimeoutMs = 100; // Short timeout to check solution flag
    }

    @Override
    public void run() {
        try {
            while (!pendingStates.isSolutionFound()) {
                BoardState state = pendingStates.poll();

                // If queue is empty, wait a bit before checking again
                if (state == null) {
                    // Check if queue is truly empty and no more work will be added
                    if (pendingStates.isEmpty()) {
                        Thread.sleep(pollTimeoutMs);
                        continue;
                    }
                    continue;
                }

                // Check if this state is the solution
                if (state.isSolved()) {
                    // Found solution! Signal all threads to stop
                    pendingStates.markSolutionFound(state);
                    return;
                }

                // Process this state: generate and queue valid successors
                processState(state);
                pendingStates.incrementStatesProcessed();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Processes a board state by generating all possible successor states.
     */
    private void processState(BoardState state) {
        Board board = state.getBoard();
        List<Move> possibleMoves = generateAllMoves(board);

        for (Move move : possibleMoves) {
            // Stop generating states if solution was found
            if (pendingStates.isSolutionFound()) {
                return;
            }

            BoardState nextState = state.applyMove(move);
            pendingStates.incrementStatesGenerated();

            // Check if this is the solution
            if (nextState.isSolved()) {
                pendingStates.markSolutionFound(nextState);
                return;
            }

            // Prune invalid states
            if (coordinator.isInvalid(nextState)) {
                pendingStates.incrementStatesPruned();
                continue;
            }

            // Valid state - add to queue for processing
            pendingStates.add(nextState);
        }
    }

    /**
     * Generates all possible moves for the given board.
     * A move swaps two tiles in the same row or column.
     *
     * @param board the board to generate moves for
     * @return list of all possible moves
     */
    private List<Move> generateAllMoves(Board board) {
        List<Move> moves = new ArrayList<>();
        int rows = board.getRowCount();
        int cols = board.getColumnCount();

        // Generate all row swaps
        for (int row = 0; row < rows; row++) {
            for (int col1 = 0; col1 < cols; col1++) {
                for (int col2 = col1 + 1; col2 < cols; col2++) {
                    // Only create move if both tiles have moves remaining
                    Tile tile1 = board.getTile(row, col1);
                    Tile tile2 = board.getTile(row, col2);
                    if (tile1.getRemainingMoves() > 0 && tile2.getRemainingMoves() > 0) {
                        moves.add(new Move(row, col1, row, col2));
                    }
                }
            }
        }

        // Generate all column swaps
        for (int col = 0; col < cols; col++) {
            for (int row1 = 0; row1 < rows; row1++) {
                for (int row2 = row1 + 1; row2 < rows; row2++) {
                    // Only create move if both tiles have moves remaining
                    Tile tile1 = board.getTile(row1, col);
                    Tile tile2 = board.getTile(row2, col);
                    if (tile1.getRemainingMoves() > 0 && tile2.getRemainingMoves() > 0) {
                        moves.add(new Move(row1, col, row2, col));
                    }
                }
            }
        }

        return moves;
    }
}
