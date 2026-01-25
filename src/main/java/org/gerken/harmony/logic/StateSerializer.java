package org.gerken.harmony.logic;

import org.gerken.harmony.model.BoardState;
import org.gerken.harmony.model.Move;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializes and deserializes board states for persistence between runs.
 *
 * State file format:
 * - One line per board state
 * - Each line starts with bestScore followed by colon, then move history as space-separated move notations
 * - Format: "bestScore:move1 move2 move3..."
 * - "INITIAL" for initial state (no moves)
 *
 * When loading, moves are replayed on the initial state to reconstruct
 * the full BoardState with proper linked list structure.
 */
public class StateSerializer {

    /**
     * Computes the state file path for a given puzzle file.
     * If puzzle file is "name.txt", state file is "name.state.txt".
     *
     * @param puzzleFile the puzzle file path
     * @return the state file path
     */
    public static String getStateFilePath(String puzzleFile) {
        if (puzzleFile.endsWith(".txt")) {
            return puzzleFile.substring(0, puzzleFile.length() - 4) + ".state.txt";
        }
        return puzzleFile + ".state.txt";
    }

    /**
     * Checks if a state file exists for the given puzzle file.
     *
     * @param puzzleFile the puzzle file path
     * @return true if a state file exists
     */
    public static boolean stateFileExists(String puzzleFile) {
        File stateFile = new File(getStateFilePath(puzzleFile));
        return stateFile.exists() && stateFile.length() > 0;
    }

    /**
     * Saves all board states to a state file.
     * States from both PendingStates queues and StateProcessor caches are saved.
     *
     * @param puzzleFile the puzzle file path (used to derive state file path)
     * @param states list of all board states to save
     * @throws IOException if writing fails
     */
    public static void saveStates(String puzzleFile, List<BoardState> states) throws IOException {
        String stateFilePath = getStateFilePath(puzzleFile);

        try (PrintWriter writer = new PrintWriter(new FileWriter(stateFilePath))) {
            writer.println("# Harmony State File");
            writer.println("# States: " + states.size());
            writer.println();

            for (BoardState state : states) {
                List<Move> history = state.getMoveHistory();
                int bestScore = state.getBestScore();
                if (history.isEmpty()) {
                    writer.println(bestScore + ":INITIAL");
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append(bestScore).append(":");
                    for (int i = 0; i < history.size(); i++) {
                        if (i > 0) sb.append(" ");
                        sb.append(history.get(i).getNotation());
                    }
                    writer.println(sb.toString());
                }
            }
        }

        System.out.println("Saved " + states.size() + " states to: " + stateFilePath);
    }

    /**
     * Loads board states from a state file.
     * Each line is parsed as a move sequence and replayed on the initial state.
     *
     * @param puzzleFile the puzzle file path (used to derive state file path)
     * @param initialState the initial board state (from puzzle file)
     * @return list of reconstructed board states
     * @throws IOException if reading fails
     */
    public static List<BoardState> loadStates(String puzzleFile, BoardState initialState) throws IOException {
        String stateFilePath = getStateFilePath(puzzleFile);
        List<BoardState> states = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(stateFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Skip comments and empty lines
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Parse bestScore prefix (format: "bestScore:moves...")
                int colonIndex = line.indexOf(':');
                int bestScore = -1;
                String movesPart = line;

                if (colonIndex > 0) {
                    try {
                        bestScore = Integer.parseInt(line.substring(0, colonIndex));
                        movesPart = line.substring(colonIndex + 1);
                    } catch (NumberFormatException e) {
                        // Fall back to treating the whole line as moves (backward compatibility)
                        movesPart = line;
                    }
                }

                // Handle initial state
                if (movesPart.equals("INITIAL")) {
                    states.add(initialState);
                    continue;
                }

                // Parse move sequence and reconstruct state
                BoardState state = reconstructState(initialState, movesPart, bestScore);
                if (state != null) {
                    states.add(state);
                }
            }
        }

        System.out.println("Loaded " + states.size() + " states from: " + stateFilePath);
        return states;
    }

    /**
     * Reconstructs a board state by replaying a sequence of moves.
     * The bestScore is recalculated during replay via applyMove.
     *
     * @param initialState the initial state to start from
     * @param moveSequence space-separated move notations
     * @param savedBestScore the bestScore that was persisted (for verification, -1 if not available)
     * @return the reconstructed board state, or null if parsing fails
     */
    private static BoardState reconstructState(BoardState initialState, String moveSequence, int savedBestScore) {
        String[] moves = moveSequence.split("\\s+");
        BoardState current = initialState;

        for (String moveStr : moves) {
            Move move = parseMove(moveStr);
            if (move == null) {
                System.err.println("Warning: Failed to parse move: " + moveStr);
                return null;
            }
            current = current.applyMove(move);
        }

        // Note: bestScore is recalculated during replay via applyMove
        // The savedBestScore parameter is available for future verification/optimization
        return current;
    }

    /**
     * Parses a move from notation (e.g., "A1-B1").
     *
     * @param notation the move notation
     * @return the parsed Move, or null if parsing fails
     */
    private static Move parseMove(String notation) {
        // Expected format: "A1-B1" where letters are rows, numbers are columns
        String[] parts = notation.split("-");
        if (parts.length != 2) {
            return null;
        }

        int[] pos1 = parsePosition(parts[0]);
        int[] pos2 = parsePosition(parts[1]);

        if (pos1 == null || pos2 == null) {
            return null;
        }

        return new Move(pos1[0], pos1[1], pos2[0], pos2[1]);
    }

    /**
     * Parses a position from notation (e.g., "A1" -> [0, 0]).
     *
     * @param position the position notation
     * @return [row, col] array, or null if parsing fails
     */
    private static int[] parsePosition(String position) {
        if (position.length() < 2) {
            return null;
        }

        char rowChar = position.charAt(0);
        String colStr = position.substring(1);

        if (rowChar < 'A' || rowChar > 'Z') {
            return null;
        }

        int row = rowChar - 'A';
        int col;
        try {
            col = Integer.parseInt(colStr) - 1;  // 1-based to 0-based
        } catch (NumberFormatException e) {
            return null;
        }

        if (col < 0) {
            return null;
        }

        return new int[] { row, col };
    }

    /**
     * Deletes the state file for a puzzle (called when solution is found).
     *
     * @param puzzleFile the puzzle file path
     */
    public static void deleteStateFile(String puzzleFile) {
        File stateFile = new File(getStateFilePath(puzzleFile));
        if (stateFile.exists()) {
            if (stateFile.delete()) {
                System.out.println("Deleted state file: " + stateFile.getPath());
            }
        }
    }
}
