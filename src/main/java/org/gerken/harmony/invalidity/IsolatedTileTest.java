package org.gerken.harmony.invalidity;

import org.gerken.harmony.model.Board;
import org.gerken.harmony.model.BoardState;
import org.gerken.harmony.model.Move;
import org.gerken.harmony.model.Tile;

import java.util.List;

/**
 * Invalidity test that detects isolated tiles that cannot make any moves.
 *
 * If a tile T has non-zero remaining moves, but every other tile in both
 * T's row AND T's column has zero remaining moves, then T cannot swap with
 * any other tile, making the board invalid.
 *
 * This is invalid because a tile needs at least one other tile (in its row
 * or column) with moves remaining to perform a swap.
 *
 * Optimization: Only checks the two tiles involved in the last move, as only
 * those tiles could have transitioned to this isolated state.
 *
 * Thread-safe singleton implementation.
 */
public class IsolatedTileTest implements InvalidityTest {

    private static final IsolatedTileTest INSTANCE = new IsolatedTileTest();

    private IsolatedTileTest() {
        // Private constructor for singleton pattern
    }

    public static IsolatedTileTest getInstance() {
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
        if (isTileIsolated(board, row1, col1)) {
            return true;
        }

        // Check second tile
        int row2 = lastMove.getRow2();
        int col2 = lastMove.getCol2();
        if (isTileIsolated(board, row2, col2)) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a specific tile is isolated (has moves but cannot swap with anyone).
     */
    private boolean isTileIsolated(Board board, int row, int col) {
        Tile tile = board.getTile(row, col);

        // Only consider tiles with non-zero remaining moves
        if (tile.getRemainingMoves() == 0) {
            return false;
        }

        // Check if any other tile in the same row has moves remaining
        for (int c = 0; c < board.getColumnCount(); c++) {
            if (c != col) {
                Tile otherTile = board.getTile(row, c);
                if (otherTile.getRemainingMoves() > 0) {
                    // Found a tile in the same row with moves - not isolated
                    return false;
                }
            }
        }

        // Check if any other tile in the same column has moves remaining
        for (int r = 0; r < board.getRowCount(); r++) {
            if (r != row) {
                Tile otherTile = board.getTile(r, col);
                if (otherTile.getRemainingMoves() > 0) {
                    // Found a tile in the same column with moves - not isolated
                    return false;
                }
            }
        }

        // No tiles in row or column have moves remaining - this tile is isolated!
        return true;
    }

    /**
     * Checks all tiles on the board (used for initial state).
     */
    private boolean checkAllTiles(Board board) {
        for (int row = 0; row < board.getRowCount(); row++) {
            for (int col = 0; col < board.getColumnCount(); col++) {
                if (isTileIsolated(board, row, col)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String getName() {
        return "IsolatedTileTest";
    }
}
