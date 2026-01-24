package org.gerken.harmony.logic;

import org.gerken.harmony.HarmonySolver;
import org.gerken.harmony.model.Board;
import org.gerken.harmony.model.BoardState;
import org.gerken.harmony.model.Move;
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
 * Supports two input formats:
 *
 * FORMAT 1 (Traditional):
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
 * FORMAT 2 (BOARD section):
 * ROWS <number>
 * COLS <number>
 * BOARD
 * <color_name> <tile1> <moves1> <tile2> <moves2> ... <tileN> <movesN>
 * ...
 *
 * In FORMAT 2, each line after BOARD defines:
 * - A color name (which becomes the target for that row index)
 * - Pairs of (position, moves) for each tile of that color
 * Colors are listed in order: first color is target for row 0, etc.
 *
 * OPTIONAL MOVES SECTION (after BOARD or TILES):
 * MOVES
 * <move1>
 * <move2>
 * ...
 *
 * Move notation: P1-P2 (e.g., A1-B1, C3-C6)
 * Moves are applied to the specified board state to arrive at the actual initial state.
 *
 * Example FORMAT 1:
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
 *
 * Example FORMAT 2 with MOVES:
 * ROWS 3
 * COLS 3
 * BOARD
 * RED A1 3 B2 2 C3 1
 * BLUE A2 2 B1 3 C2 1
 * GREEN A3 1 B3 2 C1 3
 * MOVES
 * A1-B1
 * A2-A3
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
            List<Move> moves = new ArrayList<>();

            String line;
            boolean inColorsSection = false;
            boolean inTilesSection = false;
            boolean inBoardSection = false;
            boolean inMovesSection = false;
            int boardColorIndex = 0;  // Tracks which row we're on in BOARD format

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
                    inBoardSection = false;
                } else if (line.startsWith("TARGETS ")) {
                    String[] rawTargets = line.substring(8).trim().split("\\s+");
                    targetNames = new String[rawTargets.length];
                    for (int i = 0; i < rawTargets.length; i++) {
                        targetNames[i] = rawTargets[i].toUpperCase();
                    }
                    inColorsSection = false;
                } else if (line.equals("TILES")) {
                    inTilesSection = true;
                    inColorsSection = false;
                    inBoardSection = false;
                    inMovesSection = false;
                } else if (line.equals("BOARD")) {
                    inBoardSection = true;
                    inColorsSection = false;
                    inTilesSection = false;
                    inMovesSection = false;
                    boardColorIndex = 0;
                } else if (line.equals("MOVES")) {
                    inMovesSection = true;
                    inColorsSection = false;
                    inTilesSection = false;
                    inBoardSection = false;
                } else if (inMovesSection) {
                    // Parse move: P1-P2 (e.g., A1-B1)
                    Move move = parseMove(line);
                    if (move != null) {
                        moves.add(move);
                    }
                } else if (inColorsSection) {
                    // Parse color mapping: color_name color_id
                    String[] parts = line.split("\\s+");
                    if (parts.length != 2) {
                        throw new IllegalArgumentException("Invalid color format: " + line);
                    }
                    String colorName = parts[0].toUpperCase();
                    int colorId = Integer.parseInt(parts[1]);
                    colorMap.put(colorName, colorId);
                } else if (inTilesSection) {
                    // Parse tile: position color_id moves
                    String[] parts = line.split("\\s+");
                    if (parts.length != 3) {
                        throw new IllegalArgumentException("Invalid tile format: " + line);
                    }
                    tiles.add(new TileData(parts[0].toUpperCase(), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
                } else if (inBoardSection) {
                    // Parse BOARD line: color_name tile1 moves1 tile2 moves2 ...
                    String[] parts = line.split("\\s+");
                    if (parts.length < 3 || (parts.length - 1) % 2 != 0) {
                        throw new IllegalArgumentException("Invalid BOARD line format: " + line);
                    }

                    String colorName = parts[0].toUpperCase();
                    int colorId = boardColorIndex;  // Color ID = row index in solution

                    // Add color to map
                    colorMap.put(colorName, colorId);

                    // Parse tile position and moves pairs
                    for (int i = 1; i < parts.length; i += 2) {
                        String position = parts[i].toUpperCase();
                        int tileMoves = Integer.parseInt(parts[i + 1]);
                        tiles.add(new TileData(position, colorId, tileMoves));
                    }

                    boardColorIndex++;
                }
            }

            // If using BOARD format, set targetNames from colorMap
            if (inBoardSection || (targetNames == null && !colorMap.isEmpty())) {
                // Build targetNames array from colorMap (ordered by color ID)
                targetNames = new String[colorMap.size()];
                for (Map.Entry<String, Integer> entry : colorMap.entrySet()) {
                    targetNames[entry.getValue()] = entry.getKey();
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

            // Validate no duplicate positions
            java.util.Set<String> seenPositions = new java.util.HashSet<>();
            for (TileData td : tiles) {
                if (!seenPositions.add(td.position)) {
                    throw new IllegalArgumentException(
                        "Duplicate tile position: " + td.position);
                }
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

            // Create initial board state
            BoardState boardState = new BoardState(board);

            // Apply any moves from the MOVES section
            if (!moves.isEmpty()) {
                for (Move move : moves) {
                    boardState = boardState.applyMove(move);
                }
                // Create fresh BoardState from final board (discard move history)
                // The MOVES section transforms the BOARD to the actual starting state
                boardState = new BoardState(boardState.getBoard());
            }

            // Mark the initial state as "first" (primary search path)
            boardState.setFirst(true);

            return boardState;
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

        char rowChar = Character.toUpperCase(position.charAt(0));
        int row = rowChar - 'A';

        String colStr = position.substring(1);
        int col = Integer.parseInt(colStr) - 1;

        return new int[]{row, col};
    }

    /**
     * Parses a move notation string like "A1-B1" into a Move object.
     *
     * @param notation the move notation (e.g., "A1-B1", "C3-C6")
     * @return the parsed Move, or null if the line is empty/comment
     * @throws IllegalArgumentException if the notation is invalid
     */
    private static Move parseMove(String notation) {
        // Skip comments on the same line
        int commentIndex = notation.indexOf('#');
        if (commentIndex >= 0) {
            notation = notation.substring(0, commentIndex).trim();
        }

        if (notation.isEmpty()) {
            return null;
        }

        String[] parts = notation.split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid move notation: " + notation +
                " (expected format: P1-P2, e.g., A1-B1)");
        }

        int[] pos1 = parsePosition(parts[0].trim());
        int[] pos2 = parsePosition(parts[1].trim());

        Move move = new Move(pos1[0], pos1[1], pos2[0], pos2[1]);

        // Validate that tiles are in same row or column
        if (!move.isValid()) {
            throw new IllegalArgumentException("Invalid move: tiles not in same row or column: " + notation);
        }

        return move;
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
