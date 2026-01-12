package org.gerken.harmony.model;

/**
 * Represents a tile in the puzzle.
 * Each tile has a color (represented as an integer) and a number of remaining moves.
 */
public class Tile {

    private final int color;
    private final int remainingMoves;

    /**
     * Creates a new tile with the specified color and remaining moves.
     *
     * @param color the color ID (integer)
     * @param remainingMoves the number of remaining moves
     */
    public Tile(int color, int remainingMoves) {
        this.color = color;
        this.remainingMoves = remainingMoves;
    }

    /**
     * Gets the color ID of this tile.
     *
     * @return the color ID (integer)
     */
    public int getColor() {
        return color;
    }

    /**
     * Gets the number of remaining moves for this tile.
     *
     * @return the remaining moves
     */
    public int getRemainingMoves() {
        return remainingMoves;
    }

    /**
     * Creates a new tile with the remaining moves decremented by one.
     *
     * @return a new tile with one less remaining move
     */
    public Tile decrementMoves() {
        return new Tile(color, remainingMoves - 1);
    }

    /**
     * Creates a copy of this tile.
     *
     * @return a new tile with the same color and remaining moves
     */
    public Tile copy() {
        return new Tile(color, remainingMoves);
    }

    @Override
    public String toString() {
        return color + ":" + remainingMoves;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tile tile = (Tile) o;
        return remainingMoves == tile.remainingMoves && color == tile.color;
    }

    @Override
    public int hashCode() {
        return 31 * color + remainingMoves;
    }
}
