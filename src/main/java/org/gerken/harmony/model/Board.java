package org.gerken.harmony.model;

import org.gerken.harmony.HarmonySolver;

import java.util.List;

/**
 * Represents the puzzle board - a grid of tiles with target colors for each row.
 * Rows are labeled A, B, C, etc. and columns are labeled 1, 2, 3, etc.
 * Colors are represented as integers for efficiency.
 *
 * Simplifying assumption: The target color ID for each row equals the row index
 * (row 0 has target color 0, row 1 has target color 1, etc.).
 */
public class Board {

    private final Tile[][] grid;

    /**
     * Creates a new board with the specified dimensions.
     *
     * @param rowCount the number of rows
     * @param columnCount the number of columns
     */
    public Board(int rowCount, int columnCount) {
        this.grid = new Tile[rowCount][columnCount];
    }

    /**
     * Creates a new board from an existing grid.
     *
     * @param grid the tile grid
     */
    public Board(Tile[][] grid) {
        this.grid = deepCopyGrid(grid);
    }

    /**
     * Gets the tile at the specified position.
     *
     * @param row the row index (0-based)
     * @param col the column index (0-based)
     * @return the tile at that position
     */
    public Tile getTile(int row, int col) {
        return grid[row][col];
    }

    /**
     * Sets the tile at the specified position.
     *
     * @param row the row index (0-based)
     * @param col the column index (0-based)
     * @param tile the tile to place
     */
    public void setTile(int row, int col, Tile tile) {
        grid[row][col] = tile;
    }

    /**
     * Gets the number of rows in the board.
     *
     * @return the row count
     */
    public int getRowCount() {
        return grid.length;
    }

    /**
     * Gets the number of columns in the board.
     *
     * @return the column count
     */
    public int getColumnCount() {
        return grid.length > 0 ? grid[0].length : 0;
    }

    /**
     * Gets the target color ID for the specified row.
     * By convention, the target color ID equals the row index.
     *
     * @param row the row index (0-based)
     * @return the target color ID for that row (equals row index)
     */
    public int getRowTargetColor(int row) {
        return row;
    }

    /**
     * Checks if this board is in a solved state.
     * A board is solved when all tiles match their row's target color
     * (which equals the row index) and all tiles have zero remaining moves.
     *
     * @return true if the board is solved
     */
    public boolean isSolved() {
        for (int row = 0; row < getRowCount(); row++) {
            for (int col = 0; col < getColumnCount(); col++) {
                Tile tile = grid[row][col];
                if (tile.getRemainingMoves() != 0 || tile.getColor() != row) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Creates a deep copy of the grid.
     */
    private Tile[][] deepCopyGrid(Tile[][] original) {
        Tile[][] copy = new Tile[original.length][];
        for (int i = 0; i < original.length; i++) {
            copy[i] = original[i].clone();
        }
        return copy;
    }

    /**
     * Creates a new board with two tiles swapped.
     *
     * @param row1 first tile row
     * @param col1 first tile column
     * @param row2 second tile row
     * @param col2 second tile column
     * @return a new board with the tiles swapped and moves decremented
     */
    public Board swap(int row1, int col1, int row2, int col2) {
        Board newBoard = new Board(grid);

        Tile tile1 = grid[row1][col1].decrementMoves();
        Tile tile2 = grid[row2][col2].decrementMoves();

        newBoard.setTile(row1, col1, tile2);
        newBoard.setTile(row2, col2, tile1);

        return newBoard;
    }

    /**
     * Returns a string representation of the board using color names.
     * Format: A | RED 1   | RED 0   | BLUE 2  |
     *
     * @param colorNames list of color names where index matches color ID
     * @return formatted string representation of the board
     */
    public String toString(List<String> colorNames) {
        // Calculate column width based on longest color name
        int maxColorLen = 0;
        for (String name : colorNames) {
            maxColorLen = Math.max(maxColorLen, name.length());
        }
        int cellWidth = maxColorLen + 4; // color + space + up to 2 digit moves + padding

        StringBuilder result = new StringBuilder();

        for (int row = 0; row < getRowCount(); row++) {
            char rowLabel = (char) ('A' + row);
            result.append(rowLabel).append(" ");

            for (int col = 0; col < getColumnCount(); col++) {
                Tile tile = grid[row][col];
                String colorName = colorNames.get(tile.getColor());
                // Only show moves count if non-zero
                String cell = tile.getRemainingMoves() == 0
                    ? colorName
                    : colorName + " " + tile.getRemainingMoves();
                result.append("| ");
                result.append(cell);
                // Pad to align columns
                int padding = cellWidth - cell.length() - 1;
                for (int p = 0; p < padding; p++) {
                    result.append(" ");
                }
            }
            result.append("|");
            if (row < getRowCount() - 1) {
                result.append("\n");
            }
        }

        return result.toString();
    }

    /**
     * Returns a string representation of the board.
     * Uses color names from HarmonySolver.colorNames if available,
     * otherwise falls back to color IDs.
     *
     * @return formatted string representation of the board
     */
    @Override
    public String toString() {
        // Use color names if available
        if (HarmonySolver.colorNames != null && !HarmonySolver.colorNames.isEmpty()) {
            return toString(HarmonySolver.colorNames);
        }

        // Fall back to color IDs
        StringBuilder result = new StringBuilder();

        for (int row = 0; row < getRowCount(); row++) {
            char rowLabel = (char) ('A' + row);
            result.append(rowLabel).append(" ");

            for (int col = 0; col < getColumnCount(); col++) {
                Tile tile = grid[row][col];
                result.append("| ");
                // Only show moves count if non-zero
                if (tile.getRemainingMoves() == 0) {
                    result.append(String.format("%-4d", tile.getColor()));
                } else {
                    result.append(String.format("%d:%-2d", tile.getColor(), tile.getRemainingMoves()));
                }
                result.append(" ");
            }
            result.append("|");
            if (row < getRowCount() - 1) {
                result.append("\n");
            }
        }

        return result.toString();
    }
}
