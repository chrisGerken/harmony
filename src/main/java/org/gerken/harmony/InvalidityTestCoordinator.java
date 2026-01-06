package org.gerken.harmony;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Coordinator that runs all registered invalidity tests against board states.
 * This class is thread-safe and uses a singleton pattern.
 */
public class InvalidityTestCoordinator {

    private static final InvalidityTestCoordinator INSTANCE = new InvalidityTestCoordinator();

    private final List<InvalidityTest> tests;

    private InvalidityTestCoordinator() {
        List<InvalidityTest> testList = new ArrayList<>();

        // Register invalidity test implementations here
        testList.add(StuckTileTest.getInstance());

        this.tests = Collections.unmodifiableList(testList);
    }

    public static InvalidityTestCoordinator getInstance() {
        return INSTANCE;
    }

    /**
     * Checks if the given board state is invalid by running all registered tests.
     * Returns true if ANY test determines the board state is invalid.
     *
     * @param boardState the board state to evaluate
     * @return true if the board state is invalid according to any test
     */
    public boolean isInvalid(BoardState boardState) {
        for (InvalidityTest test : tests) {
            if (test.isInvalid(boardState)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns an unmodifiable list of all registered invalidity tests.
     *
     * @return the list of tests
     */
    public List<InvalidityTest> getTests() {
        return tests;
    }

    /**
     * Returns the number of registered invalidity tests.
     *
     * @return the count of tests
     */
    public int getTestCount() {
        return tests.size();
    }
}
