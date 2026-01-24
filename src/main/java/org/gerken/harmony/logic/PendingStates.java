package org.gerken.harmony.logic;

import org.gerken.harmony.model.BoardState;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe container for pending board states and processing statistics.
 * Encapsulates multiple queues organized by board score, counters, and
 * coordination flags used by the multi-threaded solver.
 *
 * This class provides a clean abstraction layer for:
 * - Managing pending states using score-based search strategy (multiple queues by score)
 * - Tracking processing statistics (processed, generated, pruned counts)
 * - Coordinating solution discovery across threads
 *
 * The score-based approach prioritizes states with lower scores (closer to solution).
 * States are always polled from the lowest-score queue first.
 *
 * All operations are thread-safe and can be safely accessed by multiple
 * worker threads and the progress reporter simultaneously.
 */
public class PendingStates {

    private final ConcurrentLinkedQueue<BoardState>[][] queuesByScore;
    private final boolean[][] activeQueues;
    private final int maxScore;
    private final int replicationFactor;
    private volatile boolean solutionFound;
    private final AtomicReference<BoardState> solution;
    private final AtomicLong statesProcessed;
    private final AtomicLong statesGenerated;
    private final AtomicLong statesPruned;

    // Invalidity counters: key is "moveCount:testName", value is count
    private final ConcurrentHashMap<String, AtomicLong> invalidityCounters;

    /**
     * Creates a new pending states container with all counters initialized to zero.
     * Pre-creates all queues for the score-based queue structure.
     * Maximum score is initialScore + 6. States with higher scores are stored in maxScore queue.
     *
     * @param initialScore the score of the initial board state
     * @param replicationFactor the number of replicated queues per score level
     */
    @SuppressWarnings("unchecked")
    public PendingStates(int initialScore, int replicationFactor) {
        this.maxScore = initialScore + 6;
        this.replicationFactor = replicationFactor;
        this.queuesByScore = (ConcurrentLinkedQueue<BoardState>[][]) new ConcurrentLinkedQueue[maxScore + 1][replicationFactor];
        this.activeQueues = new boolean[maxScore + 1][replicationFactor];  // All false by default
        for (int i = 0; i <= maxScore; i++) {
            for (int j = 0; j < replicationFactor; j++) {
                this.queuesByScore[i][j] = new ConcurrentLinkedQueue<>();
            }
        }
        this.solutionFound = false;
        this.solution = new AtomicReference<>(null);
        this.statesProcessed = new AtomicLong(0);
        this.statesGenerated = new AtomicLong(0);
        this.statesPruned = new AtomicLong(0);
        this.invalidityCounters = new ConcurrentHashMap<>();
    }

    /**
     * Creates a new QueueContext for use by a StateProcessor.
     *
     * @return a new QueueContext instance
     */
    public QueueContext getQueueContext() {
        return new QueueContext(replicationFactor);
    }

    // ========== Queue Operations (Score-Based Strategy) ==========

    /**
     * Adds a board state to a randomly selected replicated queue at the appropriate score level.
     * States are organized by board score to prioritize lower-score states.
     * States with scores above maxScore are stored in the maxScore queue.
     * This operation is thread-safe and non-blocking.
     *
     * @param state the board state to add
     * @param context the QueueContext providing random queue selection
     */
    public void add(BoardState state, QueueContext context) {
        int score = state.getBoard().getScore();
        int scoreIndex = Math.min(score, maxScore);
        int queueIndex = context.getRandomQueueIndex();
        activeQueues[scoreIndex][queueIndex] = true;
        queuesByScore[scoreIndex][queueIndex].add(state);
    }

    /**
     * Adds a board state to the first replicated queue at the appropriate score level.
     * Used for initial state when no QueueContext is available.
     * States with scores above maxScore are stored in the maxScore queue.
     * This operation is thread-safe and non-blocking.
     *
     * @param state the board state to add
     */
    public void add(BoardState state) {
        int score = state.getBoard().getScore();
        int scoreIndex = Math.min(score, maxScore);
        activeQueues[scoreIndex][0] = true;
        queuesByScore[scoreIndex][0].add(state);
    }

    /**
     * Retrieves and removes the next pending board state using score-based strategy.
     * Always polls from the lowest score queue first (lower score = closer to solution),
     * then works upward to higher score queues. Uses the QueueContext to select which
     * replicated queue to poll. Returns null if the selected queues are empty.
     * This operation is thread-safe and non-blocking.
     *
     * @param context the QueueContext providing random queue selection
     * @return the next board state from the lowest available score queue, or null if selected queues are empty
     */
    public BoardState poll(QueueContext context) {
        int queueIndex = context.getRandomQueueIndex();
        // Try polling from lowest score to highest, only checking active queues
        for (int score = 0; score <= maxScore; score++) {
            if (!activeQueues[score][queueIndex]) {
                continue;
            }
            BoardState state = queuesByScore[score][queueIndex].poll();
            if (state != null) {
                return state;
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
        for (int i = 0; i <= maxScore; i++) {
            for (int j = 0; j < replicationFactor; j++) {
                if (!queuesByScore[i][j].isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Returns the total number of pending states across all score queues.
     * This operation is thread-safe but the value may be stale
     * by the time it's used in a multi-threaded environment.
     *
     * @return the current total size across all queues
     */
    public int size() {
        int total = 0;
        for (int i = 0; i <= maxScore; i++) {
            for (int j = 0; j < replicationFactor; j++) {
                total += queuesByScore[i][j].size();
            }
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
        return solutionFound;
    }

    /**
     * Marks that a solution has been found.
     *
     * @param solutionState the solution board state
     */
    public void markSolutionFound(BoardState solutionState) {
        solution.set(solutionState);
        solutionFound = true;
    }

    /**
     * Forcibly sets the solution found flag to true.
     * Used for coordinated shutdown even if no solution exists.
     */
    public void setSolutionFound() {
        solutionFound = true;
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
     * Adds to the count of states that have been generated.
     *
     * @param count the number to add
     */
    public void addStatesGenerated(int count) {
        statesGenerated.addAndGet(count);
    }

    /**
     * Adds to the count of states that have been pruned as invalid.
     *
     * @param count the number to add
     */
    public void addStatesPruned(int count) {
        statesPruned.addAndGet(count);
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
     * Finds the smallest score with a non-empty queue and returns its total size
     * across all replicated queues at that score level.
     * Returns an array of [score, queueSize] or null if all queues are empty.
     *
     * @return int array with [smallestScore, queueSize], or null if all queues empty
     */
    public int[] getSmallestNonEmptyQueueInfo() {
        // Search from smallest to largest score
        for (int score = 0; score <= maxScore; score++) {
            int size = 0;
            for (int j = 0; j < replicationFactor; j++) {
                size += queuesByScore[score][j].size();
            }
            if (size > 0) {
                return new int[] { score, size };
            }
        }

        return null;
    }

    /**
     * Returns queue sizes for all queues from first non-empty to last non-empty, inclusive.
     * Returns a 2D array where each element is [score, queueSize].
     * Queue size is the total across all replicated queues at that score level.
     * Returns null if all queues are empty.
     *
     * @return array of [score, queueSize] pairs, or null if all queues empty
     */
    public int[][] getQueueRangeInfo() {
        // Find first non-empty queue
        int firstNonEmpty = -1;
        for (int score = 0; score <= maxScore; score++) {
            int size = 0;
            for (int j = 0; j < replicationFactor; j++) {
                size += queuesByScore[score][j].size();
            }
            if (size > 0) {
                firstNonEmpty = score;
                break;
            }
        }

        if (firstNonEmpty == -1) {
            return null;
        }

        // Find last non-empty queue
        int lastNonEmpty = firstNonEmpty;
        for (int score = maxScore; score > firstNonEmpty; score--) {
            int size = 0;
            for (int j = 0; j < replicationFactor; j++) {
                size += queuesByScore[score][j].size();
            }
            if (size > 0) {
                lastNonEmpty = score;
                break;
            }
        }

        // Build result array for range [firstNonEmpty, lastNonEmpty]
        int rangeSize = lastNonEmpty - firstNonEmpty + 1;
        int[][] result = new int[rangeSize][2];

        for (int i = 0; i < rangeSize; i++) {
            int score = firstNonEmpty + i;
            result[i][0] = score;
            int size = 0;
            for (int j = 0; j < replicationFactor; j++) {
                size += queuesByScore[score][j].size();
            }
            result[i][1] = size;
        }

        return result;
    }

    /**
     * Collects all board states from all queues.
     * Used for state persistence when shutting down.
     *
     * @return list of all board states in the queues
     */
    public java.util.List<BoardState> collectAllStates() {
        java.util.List<BoardState> allStates = new java.util.ArrayList<>();
        for (int i = 0; i <= maxScore; i++) {
            for (int j = 0; j < replicationFactor; j++) {
                allStates.addAll(queuesByScore[i][j]);
            }
        }
        return allStates;
    }

    /**
     * Gets the maximum score value used for queue indexing.
     *
     * @return the maximum score (initialScore + 6)
     */
    public int getMaxScore() {
        return maxScore;
    }
}
