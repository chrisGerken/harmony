package org.gerken.harmony;

/**
 * Represents a move in the puzzle - swapping two tiles in the same row or column.
 * Moves are labeled using row/column labels (e.g., "A5-C5" swaps tiles at positions A5 and C5).
 */
public class Move {

    private final int row1;
    private final int col1;
    private final int row2;
    private final int col2;
    private final String notation;

    /**
     * Creates a move between two positions.
     *
     * @param row1 first tile row index (0-based)
     * @param col1 first tile column index (0-based)
     * @param row2 second tile row index (0-based)
     * @param col2 second tile column index (0-based)
     */
    public Move(int row1, int col1, int row2, int col2) {
        this.row1 = row1;
        this.col1 = col1;
        this.row2 = row2;
        this.col2 = col2;
        this.notation = toNotation(row1, col1) + "-" + toNotation(row2, col2);
    }

    public int getRow1() {
        return row1;
    }

    public int getCol1() {
        return col1;
    }

    public int getRow2() {
        return row2;
    }

    public int getCol2() {
        return col2;
    }

    /**
     * Gets the move in algebraic notation (e.g., "A5-C5").
     *
     * @return the move notation
     */
    public String getNotation() {
        return notation;
    }

    /**
     * Checks if this move is valid (tiles are in the same row or column).
     *
     * @return true if the move is valid
     */
    public boolean isValid() {
        return row1 == row2 || col1 == col2;
    }

    /**
     * Converts row and column indices to algebraic notation.
     * Rows are labeled A, B, C, etc. Columns are labeled 1, 2, 3, etc.
     */
    private String toNotation(int row, int col) {
        char rowLabel = (char) ('A' + row);
        int colLabel = col + 1;
        return "" + rowLabel + colLabel;
    }

    @Override
    public String toString() {
        return notation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Move move = (Move) o;
        return row1 == move.row1 && col1 == move.col1 &&
               row2 == move.row2 && col2 == move.col2;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (31 * row1 + col1) + row2) + col2;
    }
}
