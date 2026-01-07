package org.gerken.harmony;

/**
 * Invalidity test that detects tiles stuck in the wrong row.
 * If any tile has zero remaining moves and its color doesn't match the target color
 * for its current row, the board is invalid.
 *
 * This is invalid because a tile with 0 moves cannot be moved to a different row,
 * so if it's in the wrong row, it will remain wrong forever.
 *
 * Thread-safe singleton implementation.
 */
public class WrongRowZeroMovesTest implements InvalidityTest {

    private static final WrongRowZeroMovesTest INSTANCE = new WrongRowZeroMovesTest();

    private WrongRowZeroMovesTest() {
        // Private constructor for singleton pattern
    }

    public static WrongRowZeroMovesTest getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isInvalid(BoardState boardState) {
        Board board = boardState.getBoard();

        // Check each tile on the board
        for (int row = 0; row < board.getRowCount(); row++) {
            int targetColor = board.getRowTargetColor(row);

            for (int col = 0; col < board.getColumnCount(); col++) {
                Tile tile = board.getTile(row, col);

                // If tile has no moves left and is the wrong color for this row
                if (tile.getRemainingMoves() == 0 && tile.getColor() != targetColor) {
                    return true; // Tile is stuck in wrong row - invalid!
                }
            }
        }

        return false;
    }

    @Override
    public String getName() {
        return "WrongRowZeroMovesTest";
    }
}
