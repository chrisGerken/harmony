package org.gerken.harmony;

/**
 * Invalidity test that detects blocked swap scenarios.
 *
 * If a tile T1 has 1 remaining move and is not in its correct row, and the tile
 * in T1's target row (at the same column) has 0 remaining moves, then T1 cannot
 * reach its correct position, making the board invalid.
 *
 * Thread-safe singleton implementation.
 */
public class BlockedSwapTest implements InvalidityTest {

    private static final BlockedSwapTest INSTANCE = new BlockedSwapTest();

    private BlockedSwapTest() {
        // Private constructor for singleton pattern
    }

    public static BlockedSwapTest getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isInvalid(BoardState boardState) {
        Board board = boardState.getBoard();

        // Check each tile
        for (int row = 0; row < board.getRowCount(); row++) {
            for (int col = 0; col < board.getColumnCount(); col++) {
                Tile tile = board.getTile(row, col);

                // Only consider tiles with exactly 1 remaining move
                if (tile.getRemainingMoves() != 1) {
                    continue;
                }

                int tileColor = tile.getColor();

                // Find the target row for this tile's color
                int targetRow = findTargetRowForColor(board, tileColor);

                // If tile is already in its target row, skip
                if (row == targetRow) {
                    continue;
                }

                // Check the tile in the target row at the same column
                Tile blockingTile = board.getTile(targetRow, col);

                // If the blocking tile has 0 moves, T1 cannot swap into position
                if (blockingTile.getRemainingMoves() == 0) {
                    return true; // Invalid - tile is blocked!
                }
            }
        }

        return false;
    }

    /**
     * Finds which row has the given color as its target.
     * Returns -1 if no row has this color as target.
     */
    private int findTargetRowForColor(Board board, int color) {
        for (int row = 0; row < board.getRowCount(); row++) {
            if (board.getRowTargetColor(row) == color) {
                return row;
            }
        }
        return -1; // Color not found (shouldn't happen in valid puzzles)
    }

    @Override
    public String getName() {
        return "BlockedSwapTest";
    }
}
