package org.gerken.harmony.logic;

import org.gerken.harmony.invalidity.InvalidityTestCoordinator;
import org.gerken.harmony.model.Board;
import org.gerken.harmony.model.BoardState;
import org.gerken.harmony.model.Move;
import org.gerken.harmony.model.Tile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Worker thread that processes board states from the queue.
 * Generates successor states, checks validity, and searches for solutions.
 */
public class StateProcessor implements Runnable {

    private final ConcurrentLinkedQueue<BoardState> queue;
    private final AtomicBoolean solutionFound;
    private final AtomicReference<BoardState> solution;
    private final AtomicLong statesProcessed;
    private final AtomicLong statesGenerated;
    private final AtomicLong statesPruned;
    private final InvalidityTestCoordinator coordinator;
    private final long pollTimeoutMs;

    /**
     * Creates a new state processor.
     *
     * @param queue the shared queue of pending states
     * @param solutionFound flag to signal when solution is found
     * @param solution reference to store the solution
     * @param statesProcessed counter for processed states
     * @param statesGenerated counter for generated states
     * @param statesPruned counter for pruned states
     */
    public StateProcessor(
            ConcurrentLinkedQueue<BoardState> queue,
            AtomicBoolean solutionFound,
            AtomicReference<BoardState> solution,
            AtomicLong statesProcessed,
            AtomicLong statesGenerated,
            AtomicLong statesPruned) {
        this.queue = queue;
        this.solutionFound = solutionFound;
        this.solution = solution;
        this.statesProcessed = statesProcessed;
        this.statesGenerated = statesGenerated;
        this.statesPruned = statesPruned;
        this.coordinator = InvalidityTestCoordinator.getInstance();
        this.pollTimeoutMs = 100; // Short timeout to check solution flag
    }

    @Override
    public void run() {
        try {
            while (!solutionFound.get()) {
                BoardState state = queue.poll();

                // If queue is empty, wait a bit before checking again
                if (state == null) {
                    // Check if queue is truly empty and no more work will be added
                    if (queue.isEmpty()) {
                        Thread.sleep(pollTimeoutMs);
                        continue;
                    }
                    continue;
                }

                // Check if this state is the solution
                if (state.isSolved()) {
                    // Found solution! Signal all threads to stop
                    if (solutionFound.compareAndSet(false, true)) {
                        solution.set(state);
                    }
                    return;
                }

                // Process this state: generate and queue valid successors
                processState(state);
                statesProcessed.incrementAndGet();
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
            if (solutionFound.get()) {
                return;
            }

            BoardState nextState = state.applyMove(move);
            statesGenerated.incrementAndGet();

            // Check if this is the solution
            if (nextState.isSolved()) {
                if (solutionFound.compareAndSet(false, true)) {
                    solution.set(nextState);
                }
                return;
            }

            // Prune invalid states
            if (coordinator.isInvalid(nextState)) {
                statesPruned.incrementAndGet();
                continue;
            }

            // Valid state - add to queue for processing
            queue.add(nextState);
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
