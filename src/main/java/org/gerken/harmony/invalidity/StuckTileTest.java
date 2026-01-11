package org.gerken.harmony.invalidity;

import org.gerken.harmony.model.Board;
import org.gerken.harmony.model.BoardState;
import org.gerken.harmony.model.Move;
import org.gerken.harmony.model.Tile;

/**
 * Invalidity test that detects a stuck tile scenario:
 * If a row has all tiles with the correct target color, and all tiles have 0 remaining
 * moves except for exactly one tile with 1 remaining move, the board is invalid.
 *
 * This is invalid because the tile with 1 move cannot reduce to 0 without breaking
 * the row (swapping within the row is impossible since all other tiles have 0 moves,
 * and swapping with another row would bring in the wrong color).
 *
 * Optimization: Only checks the row(s) containing the two tiles involved in the last move,
 * as only those rows could have transitioned to the stuck state.
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
        Move lastMove = boardState.getLastMove();

        // If no moves have been made, check all rows (initial state)
        if (lastMove == null) {
            return checkAllRows(board);
        }

        // Only check the row(s) containing the tiles from the last move
        int row1 = lastMove.getRow1();
        int row2 = lastMove.getRow2();

        // Check first row
        if (isRowStuck(board, row1)) {
            return true;
        }

        // Check second row only if different from first (vertical swap)
        if (row1 != row2 && isRowStuck(board, row2)) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a specific row is in the stuck state.
     */
    private boolean isRowStuck(Board board, int row) {
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
        return allColorsCorrect &&
               tilesWithOneMoveCount == 1 &&
               tilesWithOtherMovesCount == 0 &&
               tilesWithZeroMovesCount == board.getColumnCount() - 1;
    }

    /**
     * Checks all rows on the board (used for initial state).
     */
    private boolean checkAllRows(Board board) {
        for (int row = 0; row < board.getRowCount(); row++) {
            if (isRowStuck(board, row)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getName() {
        return "StuckTileTest";
    }
}
