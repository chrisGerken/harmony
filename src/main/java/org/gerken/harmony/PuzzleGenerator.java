package org.gerken.harmony;

import org.gerken.harmony.model.Tile;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Utility to generate random solvable puzzles by working backwards from a solved state.
 *
 * Algorithm:
 * 1. Create solved board (all tiles match row colors, 0 moves)
 * 2. Make N random swaps, incrementing move counts (reverse of solving)
 * 3. Output scrambled state - guaranteed solvable in <= N moves
 *
 * Usage:
 *   java PuzzleGenerator <rows> <cols> <color1,color2,...> <num_moves> <output_file>
 *
 * Examples:
 *   java PuzzleGenerator 3 3 RED,BLUE,GREEN 10 puzzle.txt
 *   java PuzzleGenerator 4 4 RED,BLUE,GREEN,YELLOW 8 easy.txt
 *
 * Recommended difficulty levels:
 * - Easy:   2x2 with 3-5 moves, 3x3 with 5-8 moves, 4x4 with 5-10 moves
 * - Medium: 3x3 with 10-15 moves, 4x4 with 10-12 moves
 * - Hard:   3x3 with 15-20 moves, 4x4 with 12-15 moves
 * - Warning: 4x4 with 15+ moves or 5x5 puzzles may take very long to solve (minutes to hours)
 */
public class PuzzleGenerator {

    public static void main(String[] args) {
        if (args.length != 5) {
            printUsage();
            System.exit(1);
        }

        try {
            // Parse arguments
            int rows = Integer.parseInt(args[0]);
            int cols = Integer.parseInt(args[1]);
            String[] colorNames = args[2].split(",");
            int numMoves = Integer.parseInt(args[3]);
            String outputFile = args[4];

            // Validate arguments
            if (rows <= 0 || cols <= 0) {
                System.err.println("Error: rows and columns must be positive");
                System.exit(1);
            }
            if (colorNames.length == 0) {
                System.err.println("Error: must specify at least one color");
                System.exit(1);
            }
            if (colorNames.length != rows) {
                System.err.println("Error: number of colors must equal number of rows");
                System.exit(1);
            }
            if (numMoves < 0) {
                System.err.println("Error: number of moves must be non-negative");
                System.exit(1);
            }

            // Generate puzzle
            System.out.println("Generating puzzle:");
            System.out.println("  Dimensions: " + rows + "x" + cols);
            System.out.println("  Colors: " + String.join(", ", colorNames));
            System.out.println("  Moves: " + numMoves);
            System.out.println("  Output: " + outputFile);
            System.out.println();

            PuzzleData puzzle = generatePuzzle(rows, cols, colorNames, numMoves);
            writePuzzleFile(puzzle, outputFile);

            System.out.println("Puzzle generated successfully!");

        } catch (NumberFormatException e) {
            System.err.println("Error: invalid number format");
            printUsage();
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Error writing file: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Generates a puzzle by starting from a solved state and making random moves backwards.
     */
    private static PuzzleData generatePuzzle(int rows, int cols, String[] colorNames, int numMoves) {
        Random random = new Random();

        // Create color mapping
        Map<String, Integer> colorMap = new HashMap<>();
        int[] targetColors = new int[rows];
        for (int i = 0; i < colorNames.length; i++) {
            colorMap.put(colorNames[i].trim(), i);
            targetColors[i] = i;
        }

        // Create solved board (all tiles match their row's target color, all have 0 moves)
        Tile[][] grid = new Tile[rows][cols];
        for (int row = 0; row < rows; row++) {
            int targetColor = targetColors[row];
            for (int col = 0; col < cols; col++) {
                grid[row][col] = new Tile(targetColor, 0);
            }
        }

        // Make random moves backwards (increment moves instead of decrement)
        for (int moveNum = 0; moveNum < numMoves; moveNum++) {
            // Choose whether to swap within a row or column
            boolean swapInRow = random.nextBoolean();

            if (swapInRow) {
                // Swap two tiles in a random row
                int row = random.nextInt(rows);
                if (cols < 2) continue; // Need at least 2 columns to swap

                int col1 = random.nextInt(cols);
                int col2 = random.nextInt(cols);
                while (col2 == col1) {
                    col2 = random.nextInt(cols);
                }

                // Swap tiles and increment move counts
                Tile tile1 = grid[row][col1];
                Tile tile2 = grid[row][col2];
                grid[row][col1] = new Tile(tile2.getColor(), tile2.getRemainingMoves() + 1);
                grid[row][col2] = new Tile(tile1.getColor(), tile1.getRemainingMoves() + 1);

            } else {
                // Swap two tiles in a random column
                int col = random.nextInt(cols);
                if (rows < 2) continue; // Need at least 2 rows to swap

                int row1 = random.nextInt(rows);
                int row2 = random.nextInt(rows);
                while (row2 == row1) {
                    row2 = random.nextInt(rows);
                }

                // Swap tiles and increment move counts
                Tile tile1 = grid[row1][col];
                Tile tile2 = grid[row2][col];
                grid[row1][col] = new Tile(tile2.getColor(), tile2.getRemainingMoves() + 1);
                grid[row2][col] = new Tile(tile1.getColor(), tile1.getRemainingMoves() + 1);
            }
        }

        return new PuzzleData(rows, cols, colorNames, colorMap, targetColors, grid);
    }

    /**
     * Writes the puzzle to a file using the BOARD format.
     * Each line after BOARD contains: color_name tile1 moves1 tile2 moves2 ...
     */
    private static void writePuzzleFile(PuzzleData puzzle, String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Write header comment
            writer.println("# Generated Harmony Puzzle");
            writer.println("# Dimensions: " + puzzle.rows + "x" + puzzle.cols);
            writer.println();

            // Write dimensions
            writer.println("ROWS " + puzzle.rows);
            writer.println("COLS " + puzzle.cols);
            writer.println();

            // Write BOARD section
            // Each line: color_name tile1 moves1 tile2 moves2 ...
            // Colors are listed in target order (color 0 = row 0 target, etc.)
            writer.println("BOARD");
            for (int colorId = 0; colorId < puzzle.colorNames.length; colorId++) {
                StringBuilder line = new StringBuilder();
                line.append(puzzle.colorNames[colorId]);

                // Find all tiles with this color
                for (int row = 0; row < puzzle.rows; row++) {
                    for (int col = 0; col < puzzle.cols; col++) {
                        Tile tile = puzzle.grid[row][col];
                        if (tile.getColor() == colorId) {
                            char rowLabel = (char) ('A' + row);
                            int colLabel = col + 1;
                            line.append(String.format(" %c%d %d", rowLabel, colLabel, tile.getRemainingMoves()));
                        }
                    }
                }
                writer.println(line.toString());
            }
        }
    }

    private static void printUsage() {
        System.out.println("Puzzle Generator - Creates solvable Harmony puzzles");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java PuzzleGenerator <rows> <cols> <colors> <moves> <output>");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  <rows>    Number of rows (must equal number of colors)");
        System.out.println("  <cols>    Number of columns");
        System.out.println("  <colors>  Comma-separated color names (e.g., RED,BLUE,GREEN)");
        System.out.println("  <moves>   Number of random moves to make");
        System.out.println("  <output>  Output file path");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java PuzzleGenerator 2 2 RED,BLUE 5 tiny.txt");
        System.out.println("  java PuzzleGenerator 3 3 RED,BLUE,GREEN 20 medium.txt");
        System.out.println("  java PuzzleGenerator 4 4 RED,BLUE,GREEN,YELLOW 50 hard.txt");
    }

    /**
     * Helper class to hold puzzle data.
     */
    private static class PuzzleData {
        final int rows;
        final int cols;
        final String[] colorNames;
        final Map<String, Integer> colorMap;
        final int[] targetColors;
        final Tile[][] grid;

        PuzzleData(int rows, int cols, String[] colorNames, Map<String, Integer> colorMap,
                   int[] targetColors, Tile[][] grid) {
            this.rows = rows;
            this.cols = cols;
            this.colorNames = colorNames;
            this.colorMap = colorMap;
            this.targetColors = targetColors;
            this.grid = grid;
        }
    }
}
