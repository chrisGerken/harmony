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

        // Check both tiles involved in the last move
        // Each tile could be:
        // 1. A blocked tile (T1): has 1 move, not in target row, blocked by 0-move tile
        // 2. A blocking tile (T2): has 0 moves, blocking another tile with 1 move

        // Check first tile
        int row1 = lastMove.getRow1();
        int col1 = lastMove.getCol1();
        if (isTileBlocked(board, row1, col1) || isTileBlocking(board, row1, col1)) {
            return true;
        }

        // Check second tile
        int row2 = lastMove.getRow2();
        int col2 = lastMove.getCol2();
        if (isTileBlocked(board, row2, col2) || isTileBlocking(board, row2, col2)) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a specific tile with 1 remaining move is blocked from reaching its target row.
     * This checks if the tile at (row, col) is a T1 that cannot reach its destination.
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
     * Checks if a specific tile with 0 remaining moves is blocking another tile.
     * This checks if the tile at (row, col) is a T2 that is blocking some other tile
     * in the same column from reaching this row.
     *
     * A tile T2 at (row, col) blocks another tile T1 if:
     * 1. T2 has 0 remaining moves
     * 2. T1 is in the same column as T2 but different row
     * 3. T1 has exactly 1 remaining move
     * 4. T1's target row equals T2's current row (T1.color == row)
     */
    private boolean isTileBlocking(Board board, int row, int col) {
        Tile tile = board.getTile(row, col);

        // Only consider tiles with exactly 0 remaining moves (potential blockers)
        if (tile.getRemainingMoves() != 0) {
            return false;
        }

        // Check all other tiles in the same column
        for (int otherRow = 0; otherRow < board.getRowCount(); otherRow++) {
            if (otherRow == row) {
                continue;
            }

            Tile otherTile = board.getTile(otherRow, col);

            // Check if this other tile is blocked by the tile at (row, col):
            // - Has exactly 1 remaining move
            // - Needs to reach row 'row' (its color == row, since target row = color)
            // - Is not already in its target row (guaranteed since otherRow != row)
            if (otherTile.getRemainingMoves() == 1 && otherTile.getColor() == row) {
                return true;
            }
        }

        return false;
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
