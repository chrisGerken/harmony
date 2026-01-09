package org.gerken.harmony.logic;

import org.gerken.harmony.invalidity.InvalidityTestCoordinator;
import org.gerken.harmony.model.Board;
import org.gerken.harmony.model.BoardState;
import org.gerken.harmony.model.Move;
import org.gerken.harmony.model.Tile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Worker thread that processes board states from the queue.
 * Generates successor states, checks validity, and searches for solutions.
 */
public class StateProcessor implements Runnable {

    private final PendingStates pendingStates;
    private final InvalidityTestCoordinator coordinator;
    private final long pollTimeoutMs;
    private final ArrayList<BoardState> cache;
    private final int cacheThreshold;

    /**
     * Creates a new state processor with default cache threshold of 4.
     *
     * @param pendingStates the shared container of pending states and statistics
     */
    public StateProcessor(PendingStates pendingStates) {
        this(pendingStates, 4);
    }

    /**
     * Creates a new state processor with configurable cache threshold.
     *
     * @param pendingStates the shared container of pending states and statistics
     * @param cacheThreshold states with fewer than this many moves are cached locally
     */
    public StateProcessor(PendingStates pendingStates, int cacheThreshold) {
        this.pendingStates = pendingStates;
        this.coordinator = InvalidityTestCoordinator.getInstance();
        this.pollTimeoutMs = 100; // Short timeout to check solution flag
        this.cache = new ArrayList<>();
        this.cacheThreshold = cacheThreshold;
    }

    @Override
    public void run() {
        try {
            while (!pendingStates.isSolutionFound()) {
                BoardState state = getNextBoardState();

                // If queue is empty, wait a bit before checking again
                if (state == null) {
                    // Check if both cache and queue are truly empty and no more work will be added
                    if (cache.isEmpty() && pendingStates.isEmpty()) {
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

            // Valid state - add to cache or queue for processing
            storeBoardState(nextState);
        }
    }

    /**
     * Generates all possible moves for the given board with intelligent filtering.
     * A move swaps two tiles in the same row or column.
     *
     * Optimizations applied (in order):
     * 1. Only generates moves where both tiles have moves remaining
     * 2. Perfect swap detection: If two tiles in same column with 1 move each can swap
     *    into their target rows, returns ONLY that move (forces optimal endgame)
     * 3. Last-move filtering: Eliminates moves where a tile with 1 remaining move
     *    would swap with a tile not in its target row (prevents wasted moves)
     *
     * These optimizations reduce generated states by up to 91% on complex puzzles
     * while maintaining solution completeness.
     *
     * @param board the board to generate moves for
     * @return list of valid, filtered moves (may be single move if perfect swap found)
     */
    private List<Move> generateAllMoves(Board board) {
        List<Move> moves = new ArrayList<>();
        int rows = board.getRowCount();
        int cols = board.getColumnCount();

        // Build color-to-row mapping once for O(1) lookup performance
        Map<Integer, Integer> colorToTargetRow = buildColorToRowMap(board);

        // Generate all row swaps
        for (int row = 0; row < rows; row++) {
            for (int col1 = 0; col1 < cols; col1++) {
                Tile tile1 = board.getTile(row, col1);
                // Only create move if both tiles have moves remaining
                if (tile1.getRemainingMoves() > 0) {
                	for (int col2 = col1 + 1; col2 < cols; col2++) {
                        // Only create move if both tiles have moves remaining
                        Tile tile2 = board.getTile(row, col2);
                        if (tile2.getRemainingMoves() > 0) {
                            moves.add(new Move(row, col1, row, col2));
                        }
                    }
                }
            }
        }

        // Generate all column swaps
        for (int col = 0; col < cols; col++) {
            for (int row1 = 0; row1 < rows; row1++) {
                // Only create move if both tiles have moves remaining
                Tile tile1 = board.getTile(row1, col);
                if (tile1.getRemainingMoves() > 0) {
                    for (int row2 = row1 + 1; row2 < rows; row2++) {
                        // Only create move if both tiles have moves remaining
                        Tile tile2 = board.getTile(row2, col);
                        if (tile2.getRemainingMoves() > 0) {
                            moves.add(new Move(row1, col, row2, col));
                        }
                    }
                }
            }
        }

        // Check for a "perfect swap" move: two tiles in the same column, both with
        // exactly 1 move remaining, that are each in each other's target row.
        // If such a move exists, return only that move for immediate execution.
        for (Move move : moves) {
            // Only check column swaps (row1 != row2)
            if (move.getRow1() != move.getRow2()) {
                int row1 = move.getRow1();
                int col1 = move.getCol1();
                int row2 = move.getRow2();
                int col2 = move.getCol2();

                Tile tile1 = board.getTile(row1, col1);
                Tile tile2 = board.getTile(row2, col2);

                // Check if both tiles have exactly 1 move remaining
                if (tile1.getRemainingMoves() == 1 && tile2.getRemainingMoves() == 1) {
                    // Check if tile1's color matches row2's target and vice versa
                    int targetColor1 = board.getRowTargetColor(row1);
                    int targetColor2 = board.getRowTargetColor(row2);

                    if (tile1.getColor() == targetColor2 && tile2.getColor() == targetColor1) {
                        // Found a perfect swap! Return only this move
                        List<Move> perfectMove = new ArrayList<>();
                        perfectMove.add(move);
                        return perfectMove;
                    }
                }
            }
        }

        // No perfect swap found - filter out wasteful moves involving tiles with 1 move remaining.
        // Remove moves where a tile with exactly 1 move is swapping with a tile not in its target row.
        List<Move> filteredMoves = new ArrayList<>();
        for (Move move : moves) {
            int row1 = move.getRow1();
            int col1 = move.getCol1();
            int row2 = move.getRow2();
            int col2 = move.getCol2();

            Tile tile1 = board.getTile(row1, col1);
            Tile tile2 = board.getTile(row2, col2);

            // Find the target row for each tile's color using O(1) HashMap lookup
            Integer targetRowForTile1 = colorToTargetRow.get(tile1.getColor());
            Integer targetRowForTile2 = colorToTargetRow.get(tile2.getColor());

            // Handle case where color not found (shouldn't happen in valid puzzles)
            if (targetRowForTile1 == null || targetRowForTile2 == null) {
                continue;
            }

            boolean shouldInclude = true;

            // If tile1 has exactly 1 move, tile2 must be in tile1's target row
            if (tile1.getRemainingMoves() == 1 && row2 != targetRowForTile1) {
                shouldInclude = false;
            }

            // If tile2 has exactly 1 move, tile1 must be in tile2's target row
            if (tile2.getRemainingMoves() == 1 && row1 != targetRowForTile2) {
                shouldInclude = false;
            }

            if (shouldInclude) {
                filteredMoves.add(move);
            }
        }

        return filteredMoves;
    }

    /**
     * Builds a HashMap mapping color IDs to their target row indices.
     * This enables O(1) lookup performance instead of O(n) iteration.
     *
     * @param board the board to build mapping for
     * @return map from color ID to target row index
     */
    private Map<Integer, Integer> buildColorToRowMap(Board board) {
        Map<Integer, Integer> colorToRow = new HashMap<>();
        for (int row = 0; row < board.getRowCount(); row++) {
            int targetColor = board.getRowTargetColor(row);
            colorToRow.put(targetColor, row);
        }
        return colorToRow;
    }

    /**
     * Gets the next board state to process.
     * First checks the local cache for near-solution states (< cacheThreshold moves remaining).
     * Uses LIFO (stack) order to maintain depth-first search behavior and minimize cache growth.
     * If cache is empty, polls from the shared pending states queue.
     * This reduces contention on the shared ConcurrentLinkedQueue.
     *
     * @return the next board state to process, or null if no states available
     */
    private BoardState getNextBoardState() {
        if (!cache.isEmpty()) {
            return cache.remove(cache.size() - 1);  // LIFO: process most recent state
        }
        return pendingStates.poll();
    }

    /**
     * Stores a board state either in the local cache or the shared queue.
     * Board states with fewer than cacheThreshold remaining moves are cached locally to reduce
     * contention on the shared ConcurrentLinkedQueue, as these states are close
     * to solution and benefit from being processed by the same thread.
     *
     * @param state the board state to store
     */
    private void storeBoardState(BoardState state) {
        // Use cached remaining moves count from BoardState
        int movesRemaining = state.getRemainingMoves();

        // Cache near-solution states locally, send others to shared queue
        if (movesRemaining < cacheThreshold) {
            cache.add(state);
        } else {
            pendingStates.add(state);
        }
    }

    /**
     * Gets the current size of the local cache.
     * Used by ProgressReporter to include cached states in queue size calculations.
     *
     * @return the number of states in the local cache
     */
    public int getCacheSize() {
        return cache.size();
    }
}
