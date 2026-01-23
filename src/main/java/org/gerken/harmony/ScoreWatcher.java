package org.gerken.harmony;

import org.gerken.harmony.model.Board;
import org.gerken.harmony.model.BoardState;
import org.gerken.harmony.model.Move;
import org.gerken.harmony.model.Tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reports scores after each move in a puzzle solution.
 * Uses TestBuilder to generate puzzles and displays the score progression.
 * Also analyzes how the solution move ranks among all possible moves by score.
 */
public class ScoreWatcher {

    /**
     * Main entry point for ScoreWatcher.
     * Usage: java ScoreWatcher <rows> <cols> <numMoves>
     *
     * @param args command line arguments: rows, cols, numMoves
     */
    public static void main(String[] args) {
        if (args.length != 3) {
            printUsage();
            System.exit(1);
        }

        try {
            int rows = Integer.parseInt(args[0]);
            int cols = Integer.parseInt(args[1]);
            int numMoves = Integer.parseInt(args[2]);

            if (rows < 2 || cols < 2 || numMoves < 1) {
                System.err.println("Error: rows and cols must be >= 2, numMoves must be >= 1");
                System.exit(1);
            }

            reportScores(rows, cols, numMoves);

        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid number format");
            printUsage();
            System.exit(1);
        }
    }

    /**
     * Generates a puzzle and reports the score after each move.
     * Also shows the percentile ranking of each solution move among all possible moves.
     *
     * @param rows the number of rows
     * @param cols the number of columns
     * @param numMoves the number of moves
     */
    public static void reportScores(int rows, int cols, int numMoves) {
        System.out.println("=".repeat(85));
        System.out.println("Score Watcher");
        System.out.println("=".repeat(85));
        System.out.printf("Board size: %dx%d%n", rows, cols);
        System.out.printf("Number of moves: %d%n", numMoves);
        System.out.println();

        // Generate puzzle with solution
        BoardState[] solutionPath = TestBuilder.generatePuzzleWithSolution(rows, cols, numMoves);

        // Report initial state
        BoardState initialState = solutionPath[0];
        System.out.println("Initial State:");
        System.out.println(initialState.getBoard().toString());
        System.out.println();

        // Report score after each move with percentile analysis
        System.out.println("Solution Progress:");
        System.out.println("-".repeat(85));
        System.out.printf("%-5s %-10s %7s %6s %5s %5s %6s %s%n",
            "Move", "Action", "Score", "Valid", "Min", "Max", "Rank", "Percentile");
        System.out.println("-".repeat(85));

        int previousScore = initialState.getBoard().getScore();
        System.out.printf("%-5s %-10s %7d %6s %5s %5s %6s %s%n",
            "Start", "-", previousScore, "-", "-", "-", "-", "-");

        // Track statistics
        int totalMoves = 0;
        double percentileSum = 0;
        int optimalCount = 0;  // Times solution move was best possible
        int scoreIncreaseCount = 0;  // Times score increased

        for (int i = 1; i < solutionPath.length; i++) {
            BoardState previousState = solutionPath[i - 1];
            BoardState currentState = solutionPath[i];
            Move solutionMove = currentState.getLastMove();
            int solutionScore = currentState.getBoard().getScore();

            // Generate all possible moves and their resulting scores
            List<MoveScore> possibleMoves = generateAllPossibleMoves(previousState);

            // Find the rank of the solution move's score (lower score = better)
            // Sort by score ascending (best scores first)
            Collections.sort(possibleMoves, (a, b) -> Integer.compare(a.score, b.score));

            int rank = 1;
            int minScore = possibleMoves.get(0).score;
            int maxScore = possibleMoves.get(possibleMoves.size() - 1).score;
            for (MoveScore ms : possibleMoves) {
                if (ms.score < solutionScore) {
                    rank++;
                } else {
                    break;
                }
            }

            // Calculate percentile (100 = best, 0 = worst)
            // If rank 1 out of 10, percentile = 100 * (10 - 1 + 1) / 10 = 100%
            // If rank 10 out of 10, percentile = 100 * (10 - 10 + 1) / 10 = 10%
            int validMoves = possibleMoves.size();
            double percentile = 100.0 * (validMoves - rank + 1) / validMoves;

            // Track statistics
            totalMoves++;
            percentileSum += percentile;
            if (solutionScore == minScore) {
                optimalCount++;
            }

            // Only indicate when score increases (gets worse)
            String scoreChange = "";
            if (solutionScore > previousScore) {
                scoreChange = " ↑";
                scoreIncreaseCount++;
            }

            System.out.printf("%-5d %-10s %5d%-2s %6d %5d %5d %6d %6.1f%%%n",
                i,
                solutionMove.getNotation(),
                solutionScore,
                scoreChange,
                validMoves,
                minScore,
                maxScore,
                rank,
                percentile);

            previousScore = solutionScore;
        }

        System.out.println("-".repeat(85));

        // Summary statistics
        double avgPercentile = percentileSum / totalMoves;
        double optimalRate = 100.0 * optimalCount / totalMoves;
        double increaseRate = 100.0 * scoreIncreaseCount / totalMoves;

        System.out.println();
        System.out.println("Statistics:");
        System.out.printf("  Average percentile: %.1f%%%n", avgPercentile);
        System.out.printf("  Optimal moves (rank 1): %d/%d (%.1f%%)%n", optimalCount, totalMoves, optimalRate);
        System.out.printf("  Score increases: %d/%d (%.1f%%)%n", scoreIncreaseCount, totalMoves, increaseRate);
        System.out.println();

        // Final state
        BoardState finalState = solutionPath[solutionPath.length - 1];
        System.out.println("Final State:");
        System.out.println(finalState.getBoard().toString());

        if (finalState.isSolved()) {
            System.out.println("\nPuzzle SOLVED!");
        }
    }

    /**
     * Generates all possible moves from a board state and calculates their resulting scores.
     *
     * @param state the current board state
     * @return list of all possible moves with their resulting scores
     */
    private static List<MoveScore> generateAllPossibleMoves(BoardState state) {
        List<MoveScore> moves = new ArrayList<>();
        Board board = state.getBoard();
        int rows = board.getRowCount();
        int cols = board.getColumnCount();

        // Generate horizontal moves (same row)
        for (int row = 0; row < rows; row++) {
            for (int col1 = 0; col1 < cols; col1++) {
                Tile tile1 = board.getTile(row, col1);
                if (tile1.getRemainingMoves() == 0) continue;

                for (int col2 = col1 + 1; col2 < cols; col2++) {
                    Tile tile2 = board.getTile(row, col2);
                    if (tile2.getRemainingMoves() == 0) continue;

                    // Valid move found
                    Move move = new Move(row, col1, row, col2);
                    BoardState newState = state.applyMove(move);
                    int score = newState.getBoard().getScore();
                    moves.add(new MoveScore(move, score));
                }
            }
        }

        // Generate vertical moves (same column)
        for (int col = 0; col < cols; col++) {
            for (int row1 = 0; row1 < rows; row1++) {
                Tile tile1 = board.getTile(row1, col);
                if (tile1.getRemainingMoves() == 0) continue;

                for (int row2 = row1 + 1; row2 < rows; row2++) {
                    Tile tile2 = board.getTile(row2, col);
                    if (tile2.getRemainingMoves() == 0) continue;

                    // Valid move found
                    Move move = new Move(row1, col, row2, col);
                    BoardState newState = state.applyMove(move);
                    int score = newState.getBoard().getScore();
                    moves.add(new MoveScore(move, score));
                }
            }
        }

        return moves;
    }

    /**
     * Helper class to hold a move and its resulting score.
     */
    private static class MoveScore {
        final Move move;
        final int score;

        MoveScore(Move move, int score) {
            this.move = move;
            this.score = score;
        }
    }

    /**
     * Prints usage information.
     */
    private static void printUsage() {
        System.out.println("Score Watcher - Reports scores after each move in a puzzle solution");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java ScoreWatcher <rows> <cols> <numMoves>");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  rows      Number of rows (>= 2)");
        System.out.println("  cols      Number of columns (>= 2)");
        System.out.println("  numMoves  Number of moves to generate (>= 1)");
        System.out.println();
        System.out.println("Output columns:");
        System.out.println("  Move       - Move number in solution");
        System.out.println("  Action     - Move notation (e.g., A1-B1)");
        System.out.println("  Score      - Resulting board score (lower is better), ↑ if increased");
        System.out.println("  Valid      - Number of valid possible moves from that state");
        System.out.println("  Min        - Minimum score among all possible next moves");
        System.out.println("  Max        - Maximum score among all possible next moves");
        System.out.println("  Rank       - Rank of solution move by score (1 = best)");
        System.out.println("  Percentile - How good the move is (100% = best possible)");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java ScoreWatcher 6 6 40");
    }
}
