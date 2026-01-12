package org.gerken.harmony.invalidity;

import org.gerken.harmony.model.Board;
import org.gerken.harmony.model.BoardState;
import org.gerken.harmony.model.Move;
import org.gerken.harmony.model.Tile;

/**
 * Invalidity test that detects blocked swap scenarios.
 *
 * If a tile T1 has 1 remaining move and is not in its correct row, and the tile
 * in T1's target row (at the same column) has 0 remaining moves, then T1 cannot
 * reach its correct position, making the board invalid.
 *
 * Optimization: Only checks the two tiles involved in the last move, as only
 * those tiles could have transitioned to 1 remaining move.
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
        Move lastMove = boardState.getLastMove();

        // If no moves have been made, check all tiles (initial state)
        if (lastMove == null) {
            return checkAllTiles(board);
        }

        // Only check the two tiles involved in the last move

        // Check first tile
        int row1 = lastMove.getRow1();
        int col1 = lastMove.getCol1();
        if (isTileBlocked(board, row1, col1)) {
            return true;
        }

        // Check second tile
        int row2 = lastMove.getRow2();
        int col2 = lastMove.getCol2();
        if (isTileBlocked(board, row2, col2)) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a specific tile with 1 remaining move is blocked from reaching its target row.
     */
    private boolean isTileBlocked(Board board, int row, int col) {
        Tile tile = board.getTile(row, col);

        // Only consider tiles with exactly 1 remaining move
        if (tile.getRemainingMoves() != 1) {
            return false;
        }

        // Target row equals the tile's color (by convention)
        int targetRow = tile.getColor();

        // If tile is already in its target row, it's not blocked
        if (row == targetRow) {
            return false;
        }

        // Check the tile in the target row at the same column
        Tile blockingTile = board.getTile(targetRow, col);

        // If the blocking tile has 0 moves, this tile cannot swap into position
        return blockingTile.getRemainingMoves() == 0;
    }

    /**
     * Checks all tiles on the board (used for initial state).
     */
    private boolean checkAllTiles(Board board) {
        // Check each tile
        for (int row = 0; row < board.getRowCount(); row++) {
            for (int col = 0; col < board.getColumnCount(); col++) {
                if (isTileBlocked(board, row, col)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public String getName() {
        return "BlockedSwapTest";
    }
}
