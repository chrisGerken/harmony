package org.gerken.harmony.logic;

import org.gerken.harmony.HarmonySolver;
import org.gerken.harmony.model.Board;
import org.gerken.harmony.model.BoardState;
import org.gerken.harmony.model.Tile;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses puzzle input files and creates initial board states.
 *
 * Input file format:
 * # Comments start with #
 * ROWS <number>
 * COLS <number>
 * COLORS
 * <color_name> <color_id>
 * ...
 * TARGETS <color_name1> <color_name2> ...
 * TILES
 * <position> <color_id> <moves>
 * ...
 *
 * Example:
 * ROWS 3
 * COLS 3
 * COLORS
 * RED 0
 * BLUE 1
 * GREEN 2
 * TARGETS RED BLUE GREEN
 * TILES
 * A1 0 3
 * A2 1 2
 * A3 2 1
 * ...
 */
public class BoardParser {

    /**
     * Parses a puzzle file and creates the initial BoardState.
     *
     * @param filename path to the puzzle file
     * @return the initial board state
     * @throws IOException if file cannot be read
     * @throws IllegalArgumentException if file format is invalid
     */
    public static BoardState parse(String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            int rows = 0;
            int cols = 0;
            Map<String, Integer> colorMap = new HashMap<>();
            String[] targetNames = null;
            List<TileData> tiles = new ArrayList<>();

            String line;
            boolean inColorsSection = false;
            boolean inTilesSection = false;

            while ((line = reader.readLine()) != null) {
                // Stop reading if we hit the end of puzzle specification marker
                if (line.contains("End of Puzzle Specification")) {
                    break;
                }

                line = line.trim();

                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Parse sections
                if (line.startsWith("ROWS ")) {
                    rows = Integer.parseInt(line.substring(5).trim());
                } else if (line.startsWith("COLS ")) {
                    cols = Integer.parseInt(line.substring(5).trim());
                } else if (line.equals("COLORS")) {
                    inColorsSection = true;
                    inTilesSection = false;
                } else if (line.startsWith("TARGETS ")) {
                    targetNames = line.substring(8).trim().split("\\s+");
                    inColorsSection = false;
                } else if (line.equals("TILES")) {
                    inTilesSection = true;
                    inColorsSection = false;
                } else if (inColorsSection) {
                    // Parse color mapping: color_name color_id
                    String[] parts = line.split("\\s+");
                    if (parts.length != 2) {
                        throw new IllegalArgumentException("Invalid color format: " + line);
                    }
                    String colorName = parts[0];
                    int colorId = Integer.parseInt(parts[1]);
                    colorMap.put(colorName, colorId);
                } else if (inTilesSection) {
                    // Parse tile: position color_id moves
                    String[] parts = line.split("\\s+");
                    if (parts.length != 3) {
                        throw new IllegalArgumentException("Invalid tile format: " + line);
                    }
                    tiles.add(new TileData(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
                }
            }

            // Validate input
            if (rows <= 0 || cols <= 0) {
                throw new IllegalArgumentException("Invalid board dimensions");
            }
            if (colorMap.isEmpty()) {
                throw new IllegalArgumentException("No colors defined");
            }
            if (targetNames == null || targetNames.length != rows) {
                throw new IllegalArgumentException("Number of target colors must equal number of rows");
            }
            if (tiles.size() != rows * cols) {
                throw new IllegalArgumentException(
                    String.format("Expected %d tiles, got %d", rows * cols, tiles.size()));
            }

            // Convert target color names to IDs
            int[] targets = new int[targetNames.length];
            for (int i = 0; i < targetNames.length; i++) {
                String colorName = targetNames[i];
                if (!colorMap.containsKey(colorName)) {
                    throw new IllegalArgumentException("Unknown color in targets: " + colorName);
                }
                targets[i] = colorMap.get(colorName);
            }

            // Populate global color names list (indexed by color ID)
            HarmonySolver.colorNames = new ArrayList<>(colorMap.size());
            for (int i = 0; i < colorMap.size(); i++) {
                HarmonySolver.colorNames.add(null); // Pre-fill
            }
            for (Map.Entry<String, Integer> entry : colorMap.entrySet()) {
                HarmonySolver.colorNames.set(entry.getValue(), entry.getKey());
            }

            // Build the board
            Board board = new Board(rows, cols);
            for (TileData td : tiles) {
                int[] pos = parsePosition(td.position);
                board.setTile(pos[0], pos[1], new Tile(td.colorId, td.moves));
            }

            return new BoardState(board);
        }
    }

    /**
     * Parses a position string like "A1" into row and column indices.
     *
     * @param position the position string (e.g., "A1", "C5")
     * @return array of [row, col] (0-indexed)
     */
    private static int[] parsePosition(String position) {
        if (position.length() < 2) {
            throw new IllegalArgumentException("Invalid position: " + position);
        }

        char rowChar = position.charAt(0);
        int row = rowChar - 'A';

        String colStr = position.substring(1);
        int col = Integer.parseInt(colStr) - 1;

        return new int[]{row, col};
    }

    /**
     * Helper class to hold parsed tile data.
     */
    private static class TileData {
        final String position;
        final int colorId;
        final int moves;

        TileData(String position, int colorId, int moves) {
            this.position = position;
            this.colorId = colorId;
            this.moves = moves;
        }
    }
}
