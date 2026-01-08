package org.gerken.harmony.invalidity;

import org.gerken.harmony.model.Board;
import org.gerken.harmony.model.BoardState;
import org.gerken.harmony.model.Move;
import org.gerken.harmony.model.Tile;

import java.util.List;

/**
 * Invalidity test that detects a stuck tiles scenario with odd parity:
 * If a row has all tiles with the correct target color, all tiles have either 0 or 1
 * remaining moves, and there's an odd number of tiles with 1 remaining move, the board is invalid.
 *
 * This is invalid because tiles with 1 move can only be paired off within the row
 * (since all colors are correct, swapping outside the row would break the color constraint).
 * Each swap reduces two tiles' moves from 1 to 0, so an odd number of tiles with 1 move
 * can never all reach 0 simultaneously.
 *
 * Optimization: Only checks the row(s) containing the two tiles involved in the last move,
 * as only those rows could have transitioned to the stuck state.
 *
 * Thread-safe singleton implementation.
 */
public class StuckTilesTest implements InvalidityTest {

    private static final StuckTilesTest INSTANCE = new StuckTilesTest();

    private StuckTilesTest() {
        // Private constructor for singleton pattern
    }

    public static StuckTilesTest getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isInvalid(BoardState boardState) {
        Board board = boardState.getBoard();
        List<Move> moves = boardState.getMoves();

        // If no moves have been made, check all rows (initial state)
        if (moves.isEmpty()) {
            return checkAllRows(board);
        }

        // Only check the row(s) containing the tiles from the last move
        Move lastMove = moves.get(moves.size() - 1);
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
     * Checks if a specific row is in the stuck state with odd parity.
     * A row is stuck if:
     * 1. All tiles have the correct target color
     * 2. All tiles have either 0 or 1 remaining moves
     * 3. There's an odd number of tiles with 1 remaining move
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

        // Check if this row matches the invalid pattern:
        // - All colors correct
        // - No tiles with 2+ moves
        // - Odd number of tiles with 1 move (cannot pair them all off)
        return allColorsCorrect &&
               tilesWithOtherMovesCount == 0 &&
               tilesWithOneMoveCount > 0 &&
               tilesWithOneMoveCount % 2 == 1;
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
        return "StuckTilesTest";
    }
}
