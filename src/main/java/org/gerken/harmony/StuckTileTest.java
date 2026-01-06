package org.gerken.harmony;

/**
 * Invalidity test that detects a stuck tile scenario:
 * If a row has all tiles with the correct target color, and all tiles have 0 remaining
 * moves except for exactly one tile with 1 remaining move, the board is invalid.
 *
 * This is invalid because the tile with 1 move cannot reduce to 0 without breaking
 * the row (swapping within the row is impossible since all other tiles have 0 moves,
 * and swapping with another row would bring in the wrong color).
 *
 * Thread-safe singleton implementation.
 */
public class StuckTileTest implements InvalidityTest {

    private static final StuckTileTest INSTANCE = new StuckTileTest();

    private StuckTileTest() {
        // Private constructor for singleton pattern
    }

    public static StuckTileTest getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isInvalid(BoardState boardState) {
        Board board = boardState.getBoard();

        // Check each row
        for (int row = 0; row < board.getRowCount(); row++) {
            int targetColor = board.getRowTargetColor(row);

            boolean allColorsCorrect = true;
            int tilesWithOneMoveCount = 0;
            int tilesWithZeroMovesCount = 0;
            int tilesWithOtherMovesCount = 0;

            // Examine all tiles in this row
            for (int col = 0; col < board.getColumnCount(); col++) {
                Tile tile = board.getTile(row, col);

                // Check if color matches target
                if (tile.getColor() != targetColor) {
                    allColorsCorrect = false;
                    break; // No need to check further for this row
                }

                // Count tiles by remaining moves
                int moves = tile.getRemainingMoves();
                if (moves == 0) {
                    tilesWithZeroMovesCount++;
                } else if (moves == 1) {
                    tilesWithOneMoveCount++;
                } else {
                    tilesWithOtherMovesCount++;
                }
            }

            // Check if this row matches the invalid pattern
            if (allColorsCorrect &&
                tilesWithOneMoveCount == 1 &&
                tilesWithOtherMovesCount == 0 &&
                tilesWithZeroMovesCount == board.getColumnCount() - 1) {
                return true; // Invalid state detected
            }
        }

        return false;
    }

    @Override
    public String getName() {
        return "StuckTileTest";
    }
}
