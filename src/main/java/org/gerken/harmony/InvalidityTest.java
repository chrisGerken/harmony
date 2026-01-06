package org.gerken.harmony;

/**
 * Interface for thread-safe singleton classes that test whether a board state is invalid.
 * All implementations of this interface will be used to prune the search space.
 */
public interface InvalidityTest {

    /**
     * Determines if the given board state is invalid and cannot lead to a solution.
     * This method must be thread-safe as it will be called concurrently by multiple threads.
     *
     * @param boardState the board state to evaluate
     * @return true if the board state is invalid and should be pruned from the search space
     */
    boolean isInvalid(BoardState boardState);

    /**
     * Returns a descriptive name for this invalidity test.
     * Useful for logging, debugging, and progress reporting.
     *
     * @return the name of this test
     */
    String getName();
}
