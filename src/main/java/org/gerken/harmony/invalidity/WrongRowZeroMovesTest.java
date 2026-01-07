package org.gerken.harmony.invalidity;

import org.gerken.harmony.model.Board;
import org.gerken.harmony.model.BoardState;
import org.gerken.harmony.model.Move;
import org.gerken.harmony.model.Tile;

import java.util.List;

/**
 * Invalidity test that detects tiles stuck in the wrong row.
 * If any tile has zero remaining moves and its color doesn't match the target color
 * for its current row, the board is invalid.
 *
 * This is invalid because a tile with 0 moves cannot be moved to a different row,
 * so if it's in the wrong row, it will remain wrong forever.
 *
 * Optimization: Only checks the two tiles involved in the last move, as only
 * those tiles could have transitioned to 0 remaining moves.
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
        List<Move> moves = boardState.getMoves();

        // If no moves have been made, check all tiles (initial state)
        if (moves.isEmpty()) {
            return checkAllTiles(board);
        }

        // Only check the two tiles involved in the last move
        Move lastMove = moves.get(moves.size() - 1);

        // Check first tile
        int row1 = lastMove.getRow1();
        int col1 = lastMove.getCol1();
        if (isTileStuckInWrongRow(board, row1, col1)) {
            return true;
        }

        // Check second tile
        int row2 = lastMove.getRow2();
        int col2 = lastMove.getCol2();
        if (isTileStuckInWrongRow(board, row2, col2)) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a specific tile has 0 moves and is in the wrong row.
     */
    private boolean isTileStuckInWrongRow(Board board, int row, int col) {
        Tile tile = board.getTile(row, col);
        int targetColor = board.getRowTargetColor(row);
        return tile.getRemainingMoves() == 0 && tile.getColor() != targetColor;
    }

    /**
     * Checks all tiles on the board (used for initial state).
     */
    private boolean checkAllTiles(Board board) {
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
