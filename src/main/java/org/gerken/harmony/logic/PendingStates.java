package org.gerken.harmony.logic;

import org.gerken.harmony.model.BoardState;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe container for pending board states and processing statistics.
 * Encapsulates multiple queues organized by move depth, counters, and
 * coordination flags used by the multi-threaded solver.
 *
 * This class provides a clean abstraction layer for:
 * - Managing pending states using depth-first search strategy (multiple queues by move count)
 * - Tracking processing statistics (processed, generated, pruned counts)
 * - Coordinating solution discovery across threads
 *
 * The depth-first approach prevents queue explosion by always processing
 * states with the most moves first, diving deep into the search tree
 * before exploring other branches.
 *
 * All operations are thread-safe and can be safely accessed by multiple
 * worker threads and the progress reporter simultaneously.
 */
public class PendingStates {

    private final ConcurrentHashMap<Integer, ConcurrentLinkedQueue<BoardState>> queuesByMoveCount;
    private final AtomicInteger maxMoveCount;
    private final AtomicBoolean solutionFound;
    private final AtomicReference<BoardState> solution;
    private final AtomicLong statesProcessed;
    private final AtomicLong statesGenerated;
    private final AtomicLong statesPruned;

    // Invalidity counters: key is "moveCount:testName", value is count
    private final ConcurrentHashMap<String, AtomicLong> invalidityCounters;

    /**
     * Creates a new pending states container with all counters initialized to zero.
     * Initializes the depth-first queue structure.
     */
    public PendingStates() {
        this.queuesByMoveCount = new ConcurrentHashMap<>();
        this.maxMoveCount = new AtomicInteger(0);
        this.solutionFound = new AtomicBoolean(false);
        this.solution = new AtomicReference<>(null);
        this.statesProcessed = new AtomicLong(0);
        this.statesGenerated = new AtomicLong(0);
        this.statesPruned = new AtomicLong(0);
        this.invalidityCounters = new ConcurrentHashMap<>();
    }

    // ========== Queue Operations (Depth-First Strategy) ==========

    /**
     * Adds a board state to the appropriate depth-specific queue.
     * States are organized by move count to implement depth-first search.
     * This operation is thread-safe and non-blocking.
     *
     * @param state the board state to add
     */
    public void add(BoardState state) {
        int moveCount = state.getMoveCount();

        // Update max move count if this is deeper than we've seen before
        maxMoveCount.updateAndGet(current -> Math.max(current, moveCount));

        // Get or create queue for this move depth
        ConcurrentLinkedQueue<BoardState> queue = queuesByMoveCount.computeIfAbsent(
            moveCount,
            k -> new ConcurrentLinkedQueue<>()
        );

        queue.add(state);
    }

    /**
     * Retrieves and removes the next pending board state using depth-first strategy.
     * Always polls from the deepest queue (highest move count) first, then works
     * backward to shallower queues. Returns null if all queues are empty.
     * This operation is thread-safe and non-blocking.
     *
     * @return the next board state from the deepest available queue, or null if all queues are empty
     */
    public BoardState poll() {
        int currentMax = maxMoveCount.get();

        // Try polling from deepest to shallowest
        for (int moveCount = currentMax; moveCount >= 0; moveCount--) {
            ConcurrentLinkedQueue<BoardState> queue = queuesByMoveCount.get(moveCount);
            if (queue != null) {
                BoardState state = queue.poll();
                if (state != null) {
                    return state;
                }
            }
        }

        return null;
    }

    /**
     * Checks if all pending states queues are empty.
     * This operation is thread-safe.
     *
     * @return true if all queues contain no states
     */
    public boolean isEmpty() {
        for (ConcurrentLinkedQueue<BoardState> queue : queuesByMoveCount.values()) {
            if (!queue.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the total number of pending states across all depth queues.
     * This operation is thread-safe but the value may be stale
     * by the time it's used in a multi-threaded environment.
     *
     * @return the current total size across all queues
     */
    public int size() {
        int total = 0;
        for (ConcurrentLinkedQueue<BoardState> queue : queuesByMoveCount.values()) {
            total += queue.size();
        }
        return total;
    }

    // ========== Solution Coordination ==========

    /**
     * Checks if a solution has been found by any worker thread.
     *
     * @return true if a solution was found
     */
    public boolean isSolutionFound() {
        return solutionFound.get();
    }

    /**
     * Attempts to mark that a solution has been found.
     * Uses compare-and-set to ensure only one thread succeeds.
     *
     * @param solutionState the solution board state
     * @return true if this thread was the first to mark the solution
     */
    public boolean markSolutionFound(BoardState solutionState) {
        if (solutionFound.compareAndSet(false, true)) {
            solution.set(solutionState);
            return true;
        }
        return false;
    }

    /**
     * Forcibly sets the solution found flag to true.
     * Used for coordinated shutdown even if no solution exists.
     */
    public void setSolutionFound() {
        solutionFound.set(true);
    }

    /**
     * Gets the solution board state if one was found.
     *
     * @return the solution state, or null if no solution found
     */
    public BoardState getSolution() {
        return solution.get();
    }

    // ========== Statistics Counters ==========

    /**
     * Increments the count of states that have been processed.
     */
    public void incrementStatesProcessed() {
        statesProcessed.incrementAndGet();
    }

    /**
     * Increments the count of states that have been generated.
     */
    public void incrementStatesGenerated() {
        statesGenerated.incrementAndGet();
    }

    /**
     * Increments the count of states that have been pruned as invalid.
     */
    public void incrementStatesPruned() {
        statesPruned.incrementAndGet();
    }

    /**
     * Gets the current count of processed states.
     *
     * @return the number of states processed
     */
    public long getStatesProcessed() {
        return statesProcessed.get();
    }

    /**
     * Gets the current count of generated states.
     *
     * @return the number of states generated
     */
    public long getStatesGenerated() {
        return statesGenerated.get();
    }

    /**
     * Gets the current count of pruned states.
     *
     * @return the number of states pruned
     */
    public long getStatesPruned() {
        return statesPruned.get();
    }

    // ========== Invalidity Counters ==========

    /**
     * Increments the invalidity counter for a specific test at a specific move count.
     *
     * @param moveCount the number of moves made when the state was found invalid
     * @param testName the name of the invalidity test that found the state invalid
     */
    public void incrementInvalidityCounter(int moveCount, String testName) {
        String key = moveCount + ":" + testName;
        invalidityCounters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Gets the invalidity count for a specific test at a specific move count.
     *
     * @param moveCount the number of moves made
     * @param testName the name of the invalidity test
     * @return the count of states found invalid by that test at that move count
     */
    public long getInvalidityCount(int moveCount, String testName) {
        String key = moveCount + ":" + testName;
        AtomicLong counter = invalidityCounters.get(key);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Gets the maximum move count that has any invalidity data.
     *
     * @return the maximum move count with invalidity data, or -1 if none
     */
    public int getMaxInvalidityMoveCount() {
        int max = -1;
        for (String key : invalidityCounters.keySet()) {
            int moveCount = Integer.parseInt(key.split(":")[0]);
            if (moveCount > max) {
                max = moveCount;
            }
        }
        return max;
    }

    /**
     * Finds the smallest move count with a non-empty queue and returns its size.
     * Returns an array of [moveCount, queueSize] or null if all queues are empty.
     *
     * @return int array with [smallestMoveCount, queueSize], or null if all queues empty
     */
    public int[] getSmallestNonEmptyQueueInfo() {
        int currentMax = maxMoveCount.get();

        // Search from smallest to largest move count
        for (int moveCount = 0; moveCount <= currentMax; moveCount++) {
            ConcurrentLinkedQueue<BoardState> queue = queuesByMoveCount.get(moveCount);
            if (queue != null) {
                int size = queue.size();
                if (size > 0) {
                    return new int[] { moveCount, size };
                }
            }
        }

        return null;
    }

    /**
     * Returns queue sizes for all queues from first non-empty to last non-empty, inclusive.
     * Returns a 2D array where each element is [moveCount, queueSize].
     * Returns null if all queues are empty.
     *
     * @return array of [moveCount, queueSize] pairs, or null if all queues empty
     */
    public int[][] getQueueRangeInfo() {
        int currentMax = maxMoveCount.get();

        // Find first non-empty queue
        int firstNonEmpty = -1;
        for (int moveCount = 0; moveCount <= currentMax; moveCount++) {
            ConcurrentLinkedQueue<BoardState> queue = queuesByMoveCount.get(moveCount);
            if (queue != null && queue.size() > 0) {
                firstNonEmpty = moveCount;
                break;
            }
        }

        if (firstNonEmpty == -1) {
            return null;
        }

        // Find last non-empty queue
        int lastNonEmpty = firstNonEmpty;
        for (int moveCount = currentMax; moveCount > firstNonEmpty; moveCount--) {
            ConcurrentLinkedQueue<BoardState> queue = queuesByMoveCount.get(moveCount);
            if (queue != null && queue.size() > 0) {
                lastNonEmpty = moveCount;
                break;
            }
        }

        // Build result array for range [firstNonEmpty, lastNonEmpty]
        int rangeSize = lastNonEmpty - firstNonEmpty + 1;
        int[][] result = new int[rangeSize][2];

        for (int i = 0; i < rangeSize; i++) {
            int moveCount = firstNonEmpty + i;
            result[i][0] = moveCount;
            ConcurrentLinkedQueue<BoardState> queue = queuesByMoveCount.get(moveCount);
            result[i][1] = (queue != null) ? queue.size() : 0;
        }

        return result;
    }
}
