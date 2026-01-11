package org.gerken.harmony.invalidity;

import org.gerken.harmony.model.Board;
import org.gerken.harmony.model.BoardState;
import org.gerken.harmony.model.Tile;

/**
 * Invalidity test that detects stalemate conditions where no moves can be made.
 *
 * A board is in stalemate if no row or column has at least two tiles with
 * remaining moves. Since a swap requires two tiles in the same row or column,
 * if no such pair exists, the puzzle cannot progress and is invalid.
 *
 * Optimization: Early exit as soon as any row or column is found with two or
 * more tiles having remaining moves.
 *
 * Thread-safe singleton implementation.
 */
public class StalemateTest implements InvalidityTest {

    private static final StalemateTest INSTANCE = new StalemateTest();

    private StalemateTest() {
        // Private constructor for singleton pattern
    }

    public static StalemateTest getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isInvalid(BoardState boardState) {
        Board board = boardState.getBoard();
        int rows = board.getRowCount();
        int cols = board.getColumnCount();

        // Track count of tiles with moves remaining per row and column
        int[] rowCounts = new int[rows];
        int[] colCounts = new int[cols];

        // Iterate over all tiles
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                Tile tile = board.getTile(row, col);

                if (tile.getRemainingMoves() >= 1) {
                    rowCounts[row]++;
                    colCounts[col]++;

                    // Early exit: if any row or column has 2+ tiles with moves, not a stalemate
                    if (rowCounts[row] >= 2 || colCounts[col] >= 2) {
                        return false;
                    }
                }
            }
        }

        // No row or column has 2+ tiles with moves remaining - stalemate
        return true;
    }

    @Override
    public String getName() {
        return "StalemateTest";
    }
}
