package org.gerken.harmony.invalidity;

import org.gerken.harmony.model.Board;
import org.gerken.harmony.model.BoardState;
import org.gerken.harmony.model.Tile;

/**
 * Invalidity test that detects a future stuck tile scenario.
 *
 * A board is invalid if for any color ALL of these conditions are true:
 * 1. There is exactly one tile of that color in each column
 * 2. Tiles of that color on the target row must each have less than 3 remaining moves
 * 3. Tiles of that color NOT on the target row must each have exactly 1 remaining move
 * 4. The sum of remaining moves on all tiles of that color minus the count of tiles
 *    not on the target row is odd
 *
 * This detects situations where tiles will inevitably become stuck after they
 * move to their target row, because the parity of moves will be wrong.
 *
 * Thread-safe singleton implementation.
 */
public class FutureStuckTilesTest implements InvalidityTest {

    private static final FutureStuckTilesTest INSTANCE = new FutureStuckTilesTest();

    private FutureStuckTilesTest() {
        // Private constructor for singleton pattern
    }

    public static FutureStuckTilesTest getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isInvalid(BoardState boardState) {
        Board board = boardState.getBoard();
        int rows = board.getRowCount();
        int cols = board.getColumnCount();

        // Check each color
        for (int color = 0; color < rows; color++) {
            if (isColorStuck(board, color, rows, cols)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if a specific color will inevitably become stuck.
     *
     * @param board the board to check
     * @param color the color to check (also the target row index)
     * @param rows number of rows
     * @param cols number of columns
     * @return true if this color will become stuck
     */
    private boolean isColorStuck(Board board, int color, int rows, int cols) {
        int targetRow = color;  // Target row equals color by convention

        // Track: count of tiles per column, sum of moves, count not on target row
        int[] tilesPerColumn = new int[cols];
        int sumRemainingMoves = 0;
        int tilesNotOnTargetRow = 0;

        // Scan all tiles to find tiles of this color
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                Tile tile = board.getTile(row, col);

                if (tile.getColor() == color) {
                    tilesPerColumn[col]++;
                    int moves = tile.getRemainingMoves();
                    sumRemainingMoves += moves;

                    if (row == targetRow) {
                        // Condition 2: Tiles on target row must have < 3 remaining moves
                        if (moves >= 3) {
                            return false;
                        }
                    } else {
                        // Condition 3: Tiles NOT on target row must have exactly 1 move
                        if (moves != 1) {
                            return false;
                        }
                        tilesNotOnTargetRow++;
                    }
                }
            }
        }

        // Condition 1: Exactly one tile of this color in each column
        for (int col = 0; col < cols; col++) {
            if (tilesPerColumn[col] != 1) {
                return false;
            }
        }

        // Condition 4: (sum of remaining moves - tiles not on target row) is odd
        // Each tile not on target row will use exactly 1 move to reach target row,
        // so we subtract those "committed" moves to get the remaining parity
        int effectiveMoves = sumRemainingMoves - tilesNotOnTargetRow;
        return effectiveMoves % 2 == 1;
    }

    @Override
    public String getName() {
        return "FutureStuckTilesTest";
    }
}
