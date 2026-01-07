package org.gerken.harmony.logic;

import org.gerken.harmony.model.BoardState;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe container for pending board states and processing statistics.
 * Encapsulates the queue, counters, and coordination flags used by the
 * multi-threaded solver.
 *
 * This class provides a clean abstraction layer for:
 * - Managing the pending states queue
 * - Tracking processing statistics (processed, generated, pruned counts)
 * - Coordinating solution discovery across threads
 *
 * All operations are thread-safe and can be safely accessed by multiple
 * worker threads and the progress reporter simultaneously.
 */
public class PendingStates {

    private final ConcurrentLinkedQueue<BoardState> queue;
    private final AtomicBoolean solutionFound;
    private final AtomicReference<BoardState> solution;
    private final AtomicLong statesProcessed;
    private final AtomicLong statesGenerated;
    private final AtomicLong statesPruned;

    /**
     * Creates a new pending states container with all counters initialized to zero.
     */
    public PendingStates() {
        this.queue = new ConcurrentLinkedQueue<>();
        this.solutionFound = new AtomicBoolean(false);
        this.solution = new AtomicReference<>(null);
        this.statesProcessed = new AtomicLong(0);
        this.statesGenerated = new AtomicLong(0);
        this.statesPruned = new AtomicLong(0);
    }

    // ========== Queue Operations ==========

    /**
     * Adds a board state to the pending queue.
     * This operation is thread-safe and non-blocking.
     *
     * @param state the board state to add
     */
    public void add(BoardState state) {
        queue.add(state);
    }

    /**
     * Retrieves and removes the next pending board state from the queue.
     * Returns null if the queue is empty.
     * This operation is thread-safe and non-blocking.
     *
     * @return the next board state, or null if queue is empty
     */
    public BoardState poll() {
        return queue.poll();
    }

    /**
     * Checks if the pending states queue is empty.
     * This operation is thread-safe.
     *
     * @return true if the queue contains no states
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Returns the number of pending states in the queue.
     * This operation is thread-safe but the value may be stale
     * by the time it's used in a multi-threaded environment.
     *
     * @return the current size of the queue
     */
    public int size() {
        return queue.size();
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
}
