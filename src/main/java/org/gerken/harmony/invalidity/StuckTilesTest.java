package org.gerken.harmony.invalidity;

import org.gerken.harmony.model.Board;
import org.gerken.harmony.model.BoardState;
import org.gerken.harmony.model.Move;
import org.gerken.harmony.model.Tile;

import java.util.List;

/**
 * Invalidity test that detects a stuck tiles scenario with odd parity.
 *
 * For a row with target color C, the test checks:
 * 1. All tiles with color C, or all but one, must be in this row
 * 2. Each tile with color C must have 0, 1, or 2 remaining moves
 * 3. If the total remaining moves is odd, the board is invalid
 *
 * If exactly one tile (T1) with color C is not in the row:
 * - Let T2 be the tile in the target row at T1's column
 * - If T2's color == C (not "correct column"): T1 must have exactly 2 remaining moves
 * - If T2's color != C ("correct column"): T1 must have exactly 1 remaining move
 * - For the odd/even calculation: if T1 is in "correct column", its moves count as 0
 *
 * This is invalid because tiles that can only swap among themselves with an odd
 * total of remaining moves can never all reach 0 simultaneously.
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
     *
     * A row is stuck if:
     * 1. All tiles with target color C, or all but one, are in this row
     * 2. Each tile with color C has 0, 1, or 2 remaining moves
     * 3. The total remaining moves (with adjustments for out-of-row tile) is odd
     *
     * @param board the board to check
     * @param row the row index to check
     * @return true if the row is in a stuck state
     */
    private boolean isRowStuck(Board board, int row) {
        int targetColor = board.getRowTargetColor(row);
        int cols = board.getColumnCount();
        int rows = board.getRowCount();

        // Find all tiles with targetColor
        // Track those in this row and the one (if any) outside
        int tilesWithColorOutOfRow = 0;
        int t1Row = -1, t1Col = -1;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Tile tile = board.getTile(r, c);
                if (tile.getColor() == targetColor) {
                    // Check remaining moves constraint (0, 1, or 2)
                    if (tile.getRemainingMoves() > 2) {
                        return false; // Tile has 3+ moves, skip this row
                    }

                    if (r != row) {
                        tilesWithColorOutOfRow++;
                        t1Row = r;
                        t1Col = c;
                        // Early exit if more than one tile is out of row
                        if (tilesWithColorOutOfRow > 1) {
                            return false;
                        }
                    }
                }
            }
        }

        // Calculate total remaining moves for tiles with targetColor in the row
        int totalRemainingMoves = 0;
        for (int c = 0; c < cols; c++) {
            Tile tile = board.getTile(row, c);
            if (tile.getColor() == targetColor) {
                totalRemainingMoves += tile.getRemainingMoves();
            }
        }

        // Handle T1 if exactly one tile is out of the row
        if (tilesWithColorOutOfRow == 1) {
            Tile t1 = board.getTile(t1Row, t1Col);
            Tile t2 = board.getTile(row, t1Col);

            int t1Moves = t1.getRemainingMoves();
            boolean t2HasTargetColor = (t2.getColor() == targetColor);

            if (t2HasTargetColor) {
                // T2 has target color, so T1 is NOT in "correct column"
                // T1 must have exactly 2 remaining moves to continue
                if (t1Moves != 2) {
                    return false;
                }
                // Add T1's actual remaining moves to total
                totalRemainingMoves += t1Moves;
            } else {
                // T2 does NOT have target color, so T1 IS in "correct column"
                // T1 must have exactly 1 remaining move to continue
                if (t1Moves != 1) {
                    return false;
                }
                // T1's contribution is 0 (don't add anything)
            }
        }

        // If total is odd, board is invalid
        return totalRemainingMoves % 2 == 1;
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
