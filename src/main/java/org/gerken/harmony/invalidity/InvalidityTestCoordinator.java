package org.gerken.harmony.invalidity;

import org.gerken.harmony.model.BoardState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Coordinator that runs all registered invalidity tests against board states.
 * This class is thread-safe and uses a singleton pattern.
 *
 * Currently registered tests:
 * 1. StuckTilesTest - Detects rows with all correct colors but odd number of tiles with 1 move (odd parity)
 * 2. WrongRowZeroMovesTest - Detects tiles with 0 moves in wrong row (cannot move to correct row)
 * 3. BlockedSwapTest - Detects tiles with 1 move blocked by 0-move tiles in target position
 * 4. IsolatedTileTest - Detects tiles with moves remaining but no valid swap partners
 *
 * These tests provide 40-70% pruning rate (varies by puzzle) without eliminating valid solution paths.
 * Historical note: Initial tests (TooManyMovesTest, ImpossibleColorAlignmentTest, InsufficientMovesTest)
 * were removed as they were too aggressive and pruned valid paths.
 */
public class InvalidityTestCoordinator {

    private static final InvalidityTestCoordinator INSTANCE = new InvalidityTestCoordinator();

    private final List<InvalidityTest> tests;

    private InvalidityTestCoordinator() {
        List<InvalidityTest> testList = new ArrayList<>();

        // Register invalidity test implementations here
        // Order: fastest tests first for early exit optimization
        testList.add(StuckTilesTest.getInstance());
        testList.add(WrongRowZeroMovesTest.getInstance());
        testList.add(BlockedSwapTest.getInstance());
        testList.add(IsolatedTileTest.getInstance());

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
