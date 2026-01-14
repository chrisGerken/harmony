package org.gerken.harmony;

import org.gerken.harmony.model.Board;
import org.gerken.harmony.model.Move;
import org.gerken.harmony.model.Tile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds test states by starting from a solved puzzle and applying moves backwards.
 *
 * This utility reads a simplified test specification file that contains:
 * 1. Board dimensions (ROWS, COLS)
 * 2. Color names listed in target row order (first color = row A, etc.)
 * 3. A list of moves to apply (moves INCREMENT remaining move counts)
 *
 * The initial state is always the SOLVED state where:
 * - All tiles in row A have color 0 (first color) with 0 remaining moves
 * - All tiles in row B have color 1 (second color) with 0 remaining moves
 * - etc.
 *
 * Moves are applied backwards (like PuzzleGenerator) - each move swaps two tiles
 * and increments their remaining move counts by 1.
 *
 * If the specified file does not exist, creates an empty template.
 *
 * Usage:
 *   java TestBuilder <test-spec-file>
 *
 * File Format:
 * ============
 *
 *   # Test Builder Specification File
 *   # Lines starting with # are comments
 *
 *   ROWS <number>
 *   COLS <number>
 *
 *   COLORS
 *   <color_name>       # First color = row A target (ID 0)
 *   <color_name>       # Second color = row B target (ID 1)
 *   ...
 *
 *   MOVES
 *   <move_notation>
 *   ...
 *
 *   End of Moves
 *
 *   OUTPUT <filename>
 *
 * Move Notation:
 *   Moves are specified as "P1-P2" where P1 and P2 are positions like A1, B3, etc.
 *   Examples: A1-A3 (swap in row A), B2-D2 (swap in column 2)
 */
public class TestBuilder {

    public static void main(String[] args) {
        if (args.length != 1) {
            printUsage();
            System.exit(1);
        }

        String filename = args[0];
        File file = new File(filename);

        try {
            if (!file.exists()) {
                // Create empty template
                createTemplate(filename);
                System.out.println("Template file created: " + filename);
                System.out.println("Please fill in the template and run again.");
                System.exit(0);
            }

            // Parse the test specification
            TestSpec spec = parseTestSpec(filename);

            // Build solved initial state
            Board solvedBoard = buildSolvedBoard(spec.rows, spec.cols);

            System.out.println("=".repeat(60));
            System.out.println("Test Builder");
            System.out.println("=".repeat(60));
            System.out.println("Input file: " + filename);
            System.out.println("Output file: " + (spec.outputFile != null ? spec.outputFile : "(not specified)"));
            System.out.println("Board size: " + spec.rows + "x" + spec.cols);
            System.out.println("Colors: " + String.join(", ", spec.colorNames));
            System.out.println("Moves to apply: " + spec.moves.size());
            System.out.println();

            // Apply moves in reverse order (so solution matches input order)
            Board currentBoard = solvedBoard;

            if (!spec.moves.isEmpty()) {
                System.out.println("Applying moves (backwards from solved state):");
            }

            for (int i = spec.moves.size() - 1; i >= 0; i--) {
                Move move = spec.moves.get(i);
                int displayNum = spec.moves.size() - i;

                // Validate move bounds
                if (!isValidBounds(currentBoard, move)) {
                    System.err.println("Error: Move out of bounds at step " + displayNum + ": " + move.getNotation());
                    System.exit(1);
                }

                // Validate same row or column
                if (!move.isValid()) {
                    System.err.println("Error: Tiles not in same row/column at step " + displayNum + ": " + move.getNotation());
                    System.exit(1);
                }

                currentBoard = applyMoveBackwards(currentBoard, move);
                System.out.printf("  %3d. %s%n", displayNum, move.getNotation());
            }

            // Calculate remaining moves
            int remainingMoves = calculateRemainingMoves(currentBoard);

            System.out.println();
            System.out.println("=".repeat(60));
            System.out.println("Resulting State");
            System.out.println("=".repeat(60));
            System.out.println("Moves applied: " + spec.moves.size());
            System.out.println("Remaining moves to solve: " + remainingMoves);
            System.out.println();

            printBoard(currentBoard);

            // Write output puzzle file
            if (spec.outputFile == null || spec.outputFile.isEmpty()) {
                System.err.println("Error: No OUTPUT file specified");
                System.exit(1);
            }

            writePuzzleFile(currentBoard, spec.colorNames, spec.moves, spec.outputFile);
            System.out.println();
            System.out.println("Puzzle written to: " + spec.outputFile);

        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.err.println("Error in specification: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Creates an empty template file.
     */
    private static void createTemplate(String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("# Test Builder Specification File");
            writer.println("#");
            writer.println("# Starts from a SOLVED state and applies moves backwards.");
            writer.println("# Each move swaps two tiles and increments their remaining moves.");
            writer.println("# Lines starting with # are comments.");
            writer.println();
            writer.println("# =============================================================================");
            writer.println("# PUZZLE DEFINITION");
            writer.println("# =============================================================================");
            writer.println();
            writer.println("# Board dimensions");
            writer.println("ROWS 3");
            writer.println("COLS 3");
            writer.println();
            writer.println("# Color names in target row order");
            writer.println("# First color = row A target (ID 0)");
            writer.println("# Second color = row B target (ID 1), etc.");
            writer.println("COLORS");
            writer.println("RED");
            writer.println("BLUE");
            writer.println("GREEN");
            writer.println();
            writer.println("# =============================================================================");
            writer.println("# MOVES TO APPLY (backwards from solved state)");
            writer.println("# =============================================================================");
            writer.println("#");
            writer.println("# Move notation: Position1-Position2");
            writer.println("# Positions use row letter (A,B,C,...) and column number (1,2,3,...)");
            writer.println("# Examples:");
            writer.println("#   A1-A3  : Swap tiles at A1 and A3 (same row)");
            writer.println("#   B2-D2  : Swap tiles at B2 and D2 (same column)");
            writer.println("#");
            writer.println("# Each move swaps tiles and increments their remaining move counts.");
            writer.println();
            writer.println("MOVES");
            writer.println("# Add your moves here, one per line");
            writer.println("# Example:");
            writer.println("# A1-B1");
            writer.println("# A2-A3");
            writer.println();
            writer.println("End of Moves");
            writer.println();
            writer.println("# =============================================================================");
            writer.println("# OUTPUT FILE");
            writer.println("# =============================================================================");
            writer.println();
            writer.println("OUTPUT test_puzzle.txt");
        }
    }

    /**
     * Builds a solved board where each row has tiles of the correct color with 0 moves.
     * Target color for each row equals the row index.
     */
    private static Board buildSolvedBoard(int rows, int cols) {
        Board board = new Board(rows, cols);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                board.setTile(row, col, new Tile(row, 0));
            }
        }
        return board;
    }

    /**
     * Applies a move backwards - swaps tiles and increments their move counts.
     */
    private static Board applyMoveBackwards(Board board, Move move) {
        int row1 = move.getRow1();
        int col1 = move.getCol1();
        int row2 = move.getRow2();
        int col2 = move.getCol2();

        // Get current tiles
        Tile tile1 = board.getTile(row1, col1);
        Tile tile2 = board.getTile(row2, col2);

        // Create new tiles with incremented move counts
        Tile newTile1 = new Tile(tile2.getColor(), tile2.getRemainingMoves() + 1);
        Tile newTile2 = new Tile(tile1.getColor(), tile1.getRemainingMoves() + 1);

        // Create new board with swapped tiles
        Tile[][] newGrid = new Tile[board.getRowCount()][board.getColumnCount()];
        for (int r = 0; r < board.getRowCount(); r++) {
            for (int c = 0; c < board.getColumnCount(); c++) {
                if (r == row1 && c == col1) {
                    newGrid[r][c] = newTile1;
                } else if (r == row2 && c == col2) {
                    newGrid[r][c] = newTile2;
                } else {
                    newGrid[r][c] = board.getTile(r, c);
                }
            }
        }

        return new Board(newGrid);
    }

    /**
     * Calculates remaining moves (sum of all tile moves / 2).
     */
    private static int calculateRemainingMoves(Board board) {
        int total = 0;
        for (int row = 0; row < board.getRowCount(); row++) {
            for (int col = 0; col < board.getColumnCount(); col++) {
                total += board.getTile(row, col).getRemainingMoves();
            }
        }
        return total / 2;
    }

    /**
     * Parses the test specification file.
     */
    private static TestSpec parseTestSpec(String filename) throws IOException {
        TestSpec spec = new TestSpec();

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            boolean inColorsSection = false;
            boolean inMovesSection = false;

            while ((line = reader.readLine()) != null) {
                // Check for end marker
                if (line.contains("End of Moves")) {
                    inMovesSection = false;
                    continue;
                }

                String trimmed = line.trim();

                // Skip empty lines and comments
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                // Check for MOVES section (ends puzzle spec, starts moves)
                if (trimmed.equals("MOVES")) {
                    inColorsSection = false;
                    inMovesSection = true;
                    continue;
                }

                // Parse OUTPUT (can appear anywhere)
                if (trimmed.startsWith("OUTPUT ")) {
                    spec.outputFile = trimmed.substring(7).trim();
                    continue;
                }

                // Parse moves
                if (inMovesSection) {
                    Move move = parseMove(trimmed);
                    if (move != null) {
                        spec.moves.add(move);
                    }
                    continue;
                }

                // Parse puzzle specification
                if (trimmed.startsWith("ROWS ")) {
                    spec.rows = Integer.parseInt(trimmed.substring(5).trim());
                    inColorsSection = false;
                } else if (trimmed.startsWith("COLS ")) {
                    spec.cols = Integer.parseInt(trimmed.substring(5).trim());
                    inColorsSection = false;
                } else if (trimmed.equals("COLORS")) {
                    inColorsSection = true;
                } else if (inColorsSection) {
                    spec.colorNames.add(trimmed);
                }
            }

            // Validate
            if (spec.rows <= 0 || spec.cols <= 0) {
                throw new IllegalArgumentException("Invalid board dimensions");
            }
            if (spec.colorNames.isEmpty()) {
                throw new IllegalArgumentException("No colors defined");
            }
            if (spec.colorNames.size() != spec.rows) {
                throw new IllegalArgumentException(
                    String.format("Number of colors (%d) must equal number of rows (%d)",
                                  spec.colorNames.size(), spec.rows));
            }

            // Set global color names for Board.toString()
            HarmonySolver.colorNames = new ArrayList<>(spec.colorNames);
        }

        return spec;
    }

    /**
     * Parses a move notation string like "A1-B1" into a Move object.
     */
    private static Move parseMove(String notation) {
        // Skip comments that might be on the same line
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

        return new Move(pos1[0], pos1[1], pos2[0], pos2[1]);
    }

    /**
     * Parses a position string like "A1" into [row, col] indices.
     */
    private static int[] parsePosition(String position) {
        if (position.length() < 2) {
            throw new IllegalArgumentException("Invalid position: " + position);
        }

        char rowChar = Character.toUpperCase(position.charAt(0));
        int row = rowChar - 'A';

        String colStr = position.substring(1);
        int col;
        try {
            col = Integer.parseInt(colStr) - 1;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid column in position: " + position);
        }

        if (row < 0 || col < 0) {
            throw new IllegalArgumentException("Invalid position: " + position);
        }

        return new int[]{row, col};
    }

    /**
     * Validates that a move is within board bounds.
     */
    private static boolean isValidBounds(Board board, Move move) {
        return move.getRow1() >= 0 && move.getRow1() < board.getRowCount() &&
               move.getCol1() >= 0 && move.getCol1() < board.getColumnCount() &&
               move.getRow2() >= 0 && move.getRow2() < board.getRowCount() &&
               move.getCol2() >= 0 && move.getCol2() < board.getColumnCount();
    }

    /**
     * Prints the board state to console.
     */
    private static void printBoard(Board board) {
        System.out.println("Board State:");
        System.out.println();
        System.out.println(board.toString());
    }

    private static void printUsage() {
        System.out.println("Test Builder - Creates test states from solved puzzles");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java TestBuilder <test-spec-file>");
        System.out.println();
        System.out.println("If the file does not exist, an empty template will be created.");
        System.out.println();
        System.out.println("See the generated template for file format documentation.");
    }

    /**
     * Writes the puzzle file using the BOARD format.
     * Each line after BOARD contains: color_name tile1 moves1 tile2 moves2 ...
     */
    private static void writePuzzleFile(Board board, List<String> colorNames,
                                        List<Move> moves, String outputFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            writer.println("# Generated by TestBuilder");
            writer.println("# Moves to solve: " + calculateRemainingMoves(board));
            writer.println();

            writer.println("ROWS " + board.getRowCount());
            writer.println("COLS " + board.getColumnCount());
            writer.println();

            // Write BOARD section
            // Each line: color_name tile1 moves1 tile2 moves2 ...
            // Colors are listed in target order (color 0 = row 0 target, etc.)
            writer.println("BOARD");
            for (int colorId = 0; colorId < colorNames.size(); colorId++) {
                StringBuilder line = new StringBuilder();
                line.append(colorNames.get(colorId));

                // Find all tiles with this color
                for (int row = 0; row < board.getRowCount(); row++) {
                    for (int col = 0; col < board.getColumnCount(); col++) {
                        Tile tile = board.getTile(row, col);
                        if (tile.getColor() == colorId) {
                            char rowLabel = (char) ('A' + row);
                            int colLabel = col + 1;
                            line.append(String.format(" %c%d %d", rowLabel, colLabel, tile.getRemainingMoves()));
                        }
                    }
                }
                writer.println(line.toString());
            }

            // Include solution with board states after each move
            writer.println();
            writer.println("# End of Puzzle Specification");
            writer.println();
            writer.println("# Solution:");
            writeSolution(writer, board, moves);
        }
    }

    /**
     * Writes the solution showing each move and the board state after it.
     */
    private static void writeSolution(PrintWriter writer, Board startBoard, List<Move> moves) {
        Board currentBoard = startBoard;

        // Apply moves in order (solving the puzzle)
        for (int i = 0; i < moves.size(); i++) {
            Move move = moves.get(i);

            writer.printf("# %3d. %s%n", i + 1, move.getNotation());

            // Apply move forward (decrementing moves) to simulate solving
            currentBoard = applyMoveForward(currentBoard, move);

            // Print board state (prefix each line with #)
            for (String line : currentBoard.toString().split("\n")) {
                writer.println("# " + line);
            }
            writer.println("#");
        }
    }

    /**
     * Applies a move forward - swaps tiles and decrements their move counts.
     */
    private static Board applyMoveForward(Board board, Move move) {
        int row1 = move.getRow1();
        int col1 = move.getCol1();
        int row2 = move.getRow2();
        int col2 = move.getCol2();

        // Get current tiles
        Tile tile1 = board.getTile(row1, col1);
        Tile tile2 = board.getTile(row2, col2);

        // Create new tiles with decremented move counts
        Tile newTile1 = new Tile(tile2.getColor(), tile2.getRemainingMoves() - 1);
        Tile newTile2 = new Tile(tile1.getColor(), tile1.getRemainingMoves() - 1);

        // Create new board with swapped tiles
        Tile[][] newGrid = new Tile[board.getRowCount()][board.getColumnCount()];
        for (int r = 0; r < board.getRowCount(); r++) {
            for (int c = 0; c < board.getColumnCount(); c++) {
                if (r == row1 && c == col1) {
                    newGrid[r][c] = newTile1;
                } else if (r == row2 && c == col2) {
                    newGrid[r][c] = newTile2;
                } else {
                    newGrid[r][c] = board.getTile(r, c);
                }
            }
        }

        return new Board(newGrid);
    }

    /**
     * Holds parsed test specification data.
     */
    private static class TestSpec {
        int rows;
        int cols;
        List<String> colorNames = new ArrayList<>();
        List<Move> moves = new ArrayList<>();
        String outputFile;
    }
}
