package org.gerken.harmony;

import org.gerken.harmony.invalidity.InvalidityTestCoordinator;
import org.gerken.harmony.logic.BoardParser;
import org.gerken.harmony.logic.PendingStates;
import org.gerken.harmony.logic.ProgressReporter;
import org.gerken.harmony.logic.StateProcessor;
import org.gerken.harmony.logic.StateSerializer;
import org.gerken.harmony.logic.QueueContext;
import org.gerken.harmony.model.BoardState;
import org.gerken.harmony.model.Move;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main class for the Harmony Puzzle Solver.
 * A multi-threaded solver for color-matching tile puzzles using depth-first search with pruning.
 *
 * Architecture:
 * - Uses parallel DFS with PendingStates container for state management
 * - Multiple queues organized by move depth prevent queue explosion
 * - Always processes deepest states first to find solutions quickly
 * - Employs 3 invalidity tests for intelligent pruning (40-70% pruning rate)
 * - Worker threads process states in parallel, coordinator detects solution
 * - Progress reporter provides periodic status updates
 *
 * Usage:
 *   java HarmonySolver [-t <threads>] [-r <seconds>] <puzzle-file>
 *
 * Options:
 *   -t <threads>   Number of worker threads (default: 2)
 *   -r <seconds>   Progress report interval (default: 30)
 *
 * Examples:
 *   java HarmonySolver puzzle.txt
 *   java HarmonySolver -t 2 -r 10 puzzle.txt
 *
 * Performance:
 * - Depth-first strategy significantly reduces memory usage
 * - 3x3 puzzles with 10 moves: typically <5s
 * - 4x4 puzzles with 8 moves: typically <2s
 * - Queue size remains manageable even for large puzzles
 */
public class HarmonySolver {

    private static final int DEFAULT_THREAD_COUNT = 2;
    private static final int DEFAULT_REPORT_INTERVAL = 30; // seconds
    private static final int DEFAULT_CACHE_THRESHOLD = 4; // moves remaining
    private static final boolean DEFAULT_DEBUG_MODE = false;
    private static final int DEFAULT_REPLICATION_FACTOR = 3;
    private static final int DEFAULT_DURATION_MINUTES = 120;

    /** Sort mode for move ordering in state processing. */
    public enum SortMode { NONE, SMALLEST_FIRST, LARGEST_FIRST }

    /**
     * Global color name mapping. Index is color ID, value is color name.
     * Set by BoardParser when loading a puzzle.
     */
    public static List<String> colorNames = new ArrayList<>();

    // Instance fields for solver state
    private final Config config;
    private PendingStates pendingStates;
    private List<StateProcessor> processors;
    private int initialRemainingMoves;
    private final AtomicBoolean timeExpired = new AtomicBoolean(false);
    private ExecutorService workerPool;
    private BoardState initialState;

    /**
     * Creates a new solver with the given configuration.
     *
     * @param config the solver configuration
     */
    public HarmonySolver(Config config) {
        this.config = config;
        this.processors = new ArrayList<>();
    }

    /**
     * Gets the pending states container.
     *
     * @return the pending states
     */
    public PendingStates getPendingStates() {
        return pendingStates;
    }

    /**
     * Gets the list of state processors.
     *
     * @return unmodifiable list of processors
     */
    public List<StateProcessor> getProcessors() {
        return processors;
    }

    /**
     * Gets the configured thread count.
     *
     * @return the number of worker threads
     */
    public int getThreadCount() {
        return config.threadCount;
    }

    /**
     * Gets the configured report interval.
     *
     * @return the report interval in seconds
     */
    public int getReportInterval() {
        return config.reportInterval;
    }

    /**
     * Gets the configured cache threshold.
     *
     * @return the cache threshold in moves remaining
     */
    public int getCacheThreshold() {
        return config.cacheThreshold;
    }

    /**
     * Gets whether to display invalidity statistics instead of queue sizes.
     *
     * @return true if invalidity stats should be displayed
     */
    public boolean isInvalidityStats() {
        return config.invalidityStats;
    }

    /**
     * Gets the sort mode for move ordering.
     *
     * @return the sort mode
     */
    public SortMode getSortMode() {
        return config.sortMode;
    }

    /**
     * Gets the initial number of moves required to solve the puzzle.
     *
     * @return the initial remaining moves count
     */
    public int getInitialRemainingMoves() {
        return initialRemainingMoves;
    }

    /**
     * Main entry point for the solver.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        // Parse command-line arguments
        Config config = parseArguments(args);

        if (config == null) {
            printUsage();
            System.exit(1);
        }

        // Print header
        printHeader(config);

        try {
            // Load and parse the puzzle
            System.out.println("Loading puzzle from: " + config.puzzleFile);
            BoardState initialState = BoardParser.parse(config.puzzleFile);
            System.out.println("Puzzle loaded successfully.");
            System.out.println("Board size: " + initialState.getBoard().getRowCount() +
                             "x" + initialState.getBoard().getColumnCount());
            System.out.println("Moves required: " + initialState.getRemainingMoves());
            System.out.println();

            // Check if already solved
            if (initialState.isSolved()) {
                System.out.println("Puzzle is already solved!");
                StateSerializer.deleteStateFile(config.puzzleFile);
                System.exit(0);
            }

            // Check for existing state file
            List<BoardState> resumeStates = null;
            if (StateSerializer.stateFileExists(config.puzzleFile)) {
                System.out.println("Found existing state file, resuming...");
                resumeStates = StateSerializer.loadStates(config.puzzleFile, initialState);
                System.out.println();
            }

            // Create solver instance and solve the puzzle
            HarmonySolver solver = new HarmonySolver(config);
            int result = solver.solve(initialState, resumeStates);

            // Handle result: 0 = solution found, 1 = no solution, 2 = time expired (state saved)
            if (result == 0) {
                BoardState solution = solver.pendingStates.getSolution();
                printSolution(solution, config.puzzleFile);
                StateSerializer.deleteStateFile(config.puzzleFile);
                System.exit(0);
            } else if (result == 2) {
                System.out.println("\nTime limit reached. State saved for resumption.");
                System.exit(0);
            } else {
                System.out.println("No solution found. The puzzle may be unsolvable.");
                StateSerializer.deleteStateFile(config.puzzleFile);
                System.exit(1);
            }

        } catch (IOException e) {
            System.err.println("Error reading puzzle file: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error solving puzzle: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Solves the puzzle using multi-threaded depth-first search with pruning.
     *
     * @param initialState the starting board state
     * @param resumeStates states to resume from (loaded from state file), or null for fresh start
     * @return result code: 0 = solution found, 1 = no solution, 2 = time expired (state saved)
     */
    private int solve(BoardState initialState, List<BoardState> resumeStates) {
        this.initialState = initialState;

        // Initialize shared data structures
        this.initialRemainingMoves = initialState.getRemainingMoves();
        this.pendingStates = new PendingStates(initialRemainingMoves, config.replicationFactor);

        // Add states to queue - either resume states or initial state
        if (resumeStates != null && !resumeStates.isEmpty()) {
            System.out.println("Adding " + resumeStates.size() + " resumed states to queues...");
            QueueContext ctx = pendingStates.getQueueContext();
            for (BoardState state : resumeStates) {
                pendingStates.add(state, ctx);
            }
        } else {
            pendingStates.add(initialState);
        }

        // Create worker threads
        this.workerPool = Executors.newFixedThreadPool(config.threadCount);
        this.processors = new ArrayList<>();

        System.out.println("Starting " + config.threadCount + " worker threads...");
        for (int i = 0; i < config.threadCount; i++) {
            StateProcessor processor = new StateProcessor(pendingStates, config.cacheThreshold, config.sortMode, config.invalidityStats);
            processors.add(processor);
            workerPool.submit(processor);
        }

        // Schedule timer for duration limit
        ScheduledExecutorService timerExecutor = Executors.newSingleThreadScheduledExecutor();
        timerExecutor.schedule(() -> {
            timeExpired.set(true);
            pendingStates.setSolutionFound();  // Signal threads to stop
        }, config.durationMinutes, TimeUnit.MINUTES);

        // Create and start progress reporter thread (unless disabled)
        ProgressReporter reporter = null;
        if (config.reportInterval > 0) {
            reporter = new ProgressReporter(this);
            Thread reporterThread = new Thread(reporter);
            reporterThread.setDaemon(true);
            reporterThread.start();
        }

        System.out.println("Search started (will run for " + config.durationMinutes + " minutes)...\n");

        // Wait for solution, queue exhaustion, or time expiration
        try {
            // Check periodically if we should stop
            while (!pendingStates.isSolutionFound()) {
                Thread.sleep(1000);

                // Check if queue is empty and no more work will be generated
                // Skip this check in debug mode to allow pausing at breakpoints
                if (!config.debugMode &&
                    pendingStates.isEmpty() &&
                    pendingStates.getStatesProcessed() == pendingStates.getStatesGenerated()) {
                    // All generated states have been processed
                    // If we haven't found a solution, puzzle is unsolvable
                    break;
                }
            }

            // Shutdown timer
            timerExecutor.shutdownNow();

            // Shutdown worker threads
            pendingStates.setSolutionFound(); // Signal threads to stop accepting new work
            workerPool.shutdown();

            // If time expired, wait longer for in-progress states to finish
            int waitSeconds = timeExpired.get() ? 10 : 5;
            if (timeExpired.get()) {
                System.out.println("\nTime limit reached. Waiting " + waitSeconds + " seconds for in-progress states to finish...");
            }
            workerPool.awaitTermination(waitSeconds, TimeUnit.SECONDS);

            // Print final summary (if reporting enabled)
            if (reporter != null) {
                reporter.printFinalSummary(pendingStates.getSolution() != null);
            }

            // Check if time expired
            if (timeExpired.get() && pendingStates.getSolution() == null) {
                // Save state for resumption
                saveState();
                return 2;  // Time expired
            }

            // Check if solution found
            if (pendingStates.getSolution() != null) {
                return 0;  // Solution found
            }

            return 1;  // No solution

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            timerExecutor.shutdownNow();
            workerPool.shutdownNow();
            return 1;
        }
    }

    /**
     * Saves the current state for later resumption.
     * Collects states from PendingStates queues and StateProcessor caches.
     */
    private void saveState() {
        try {
            List<BoardState> allStates = new ArrayList<>();

            // Collect states from PendingStates queues
            allStates.addAll(pendingStates.collectAllStates());

            // Collect states from StateProcessor caches
            for (StateProcessor processor : processors) {
                allStates.addAll(processor.getCachedStates());
            }

            System.out.println("\nCollected " + allStates.size() + " states for persistence");
            StateSerializer.saveStates(config.puzzleFile, allStates);

        } catch (IOException e) {
            System.err.println("Error saving state: " + e.getMessage());
        }
    }

    /**
     * Prints the solution (sequence of moves) to console and writes it to a solution file.
     * If puzzle file is "name.txt", solution file will be "name.solution.txt".
     *
     * @param solution the solution board state
     * @param puzzleFile the puzzle file path (used to derive solution file path)
     */
    private static void printSolution(BoardState solution, String puzzleFile) {
        List<Move> moves = solution.getMoveHistory();

        System.out.println("\nSOLUTION FOUND!");
        System.out.println("Number of moves: " + moves.size());
        System.out.println("\nMove sequence:");

        for (int i = 0; i < moves.size(); i++) {
            System.out.printf("%3d. %s%n", i + 1, moves.get(i).getNotation());
        }

        // Write solution to file
        String solutionFile = getSolutionFilePath(puzzleFile);
        try (PrintWriter writer = new PrintWriter(new FileWriter(solutionFile))) {
            writer.println("# Harmony Puzzle Solution");
            writer.println("# Puzzle: " + puzzleFile);
            writer.println("# Moves: " + moves.size());
            writer.println();

            // Section 1: Move sequence
            writer.println("Move sequence:");
            for (int i = 0; i < moves.size(); i++) {
                writer.printf("%3d. %s%n", i + 1, moves.get(i).getNotation());
            }

            // Section 2: Moves with board states
            writer.println();
            writer.println("=".repeat(60));
            writer.println("Step-by-step board states:");
            writer.println("=".repeat(60));

            // Get initial board by traversing back through the state chain
            BoardState initialState = solution;
            while (initialState.getPreviousBoardState() != null) {
                initialState = initialState.getPreviousBoardState();
            }

            // Show initial board
            writer.println();
            writer.println("Initial state:");
            writer.println(initialState.getBoard().toString());

            // Apply each move and show the resulting board
            org.gerken.harmony.model.Board currentBoard = initialState.getBoard();
            for (int i = 0; i < moves.size(); i++) {
                Move move = moves.get(i);
                currentBoard = currentBoard.swap(
                    move.getRow1(), move.getCol1(),
                    move.getRow2(), move.getCol2());

                writer.println();
                writer.printf("After move %d: %s%n", i + 1, move.getNotation());
                writer.println(currentBoard.toString());
            }

            System.out.println("\nSolution written to: " + solutionFile);
        } catch (IOException e) {
            System.err.println("Warning: Could not write solution file: " + e.getMessage());
        }
    }

    /**
     * Computes the solution file path for a given puzzle file.
     * If puzzle file is "name.txt", solution file is "name.solution.txt".
     *
     * @param puzzleFile the puzzle file path
     * @return the solution file path
     */
    private static String getSolutionFilePath(String puzzleFile) {
        if (puzzleFile.endsWith(".txt")) {
            return puzzleFile.substring(0, puzzleFile.length() - 4) + ".solution.txt";
        }
        return puzzleFile + ".solution.txt";
    }

    /**
     * Parses command-line arguments.
     */
    private static Config parseArguments(String[] args) {
        Config config = new Config();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg.equals("-t") || arg.equals("--threads")) {
                if (i + 1 >= args.length) {
                    System.err.println("Error: " + arg + " requires a value");
                    return null;
                }
                try {
                    config.threadCount = Integer.parseInt(args[++i]);
                    if (config.threadCount < 1) {
                        System.err.println("Error: thread count must be positive");
                        return null;
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Error: invalid thread count: " + args[i]);
                    return null;
                }
            } else if (arg.equals("-r") || arg.equals("--report")) {
                if (i + 1 >= args.length) {
                    System.err.println("Error: " + arg + " requires a value");
                    return null;
                }
                try {
                    config.reportInterval = Integer.parseInt(args[++i]);
                    if (config.reportInterval < 0) {
                        System.err.println("Error: report interval must be non-negative (0 to disable)");
                        return null;
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Error: invalid report interval: " + args[i]);
                    return null;
                }
            } else if (arg.equals("-c") || arg.equals("--cache")) {
                if (i + 1 >= args.length) {
                    System.err.println("Error: " + arg + " requires a value");
                    return null;
                }
                try {
                    config.cacheThreshold = Integer.parseInt(args[++i]);
                    if (config.cacheThreshold < 0) {
                        System.err.println("Error: cache threshold must be non-negative");
                        return null;
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Error: invalid cache threshold: " + args[i]);
                    return null;
                }
            } else if (arg.equals("-d") || arg.equals("--debug")) {
                config.debugMode = true;
            } else if (arg.equals("-i") || arg.equals("--invalidity")) {
                config.invalidityStats = true;
            } else if (arg.equals("--smallestFirst")) {
                if (config.sortMode == SortMode.LARGEST_FIRST) {
                    System.err.println("Error: --smallestFirst and --largestFirst are mutually exclusive");
                    return null;
                }
                config.sortMode = SortMode.SMALLEST_FIRST;
            } else if (arg.equals("--largestFirst")) {
                if (config.sortMode == SortMode.SMALLEST_FIRST) {
                    System.err.println("Error: --smallestFirst and --largestFirst are mutually exclusive");
                    return null;
                }
                config.sortMode = SortMode.LARGEST_FIRST;
            } else if (arg.equals("-repl")) {
                if (i + 1 >= args.length) {
                    System.err.println("Error: " + arg + " requires a value");
                    return null;
                }
                try {
                    config.replicationFactor = Integer.parseInt(args[++i]);
                    if (config.replicationFactor < 1) {
                        System.err.println("Error: replication factor must be positive");
                        return null;
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Error: invalid replication factor: " + args[i]);
                    return null;
                }
            } else if (arg.equals("-dur") || arg.equals("--duration")) {
                if (i + 1 >= args.length) {
                    System.err.println("Error: " + arg + " requires a value");
                    return null;
                }
                String durationStr = args[++i];
                int durationMinutes = parseDuration(durationStr);
                if (durationMinutes < 0) {
                    System.err.println("Error: invalid duration: " + durationStr);
                    return null;
                }
                if (durationMinutes < 1) {
                    System.err.println("Error: duration must be at least 1 minute");
                    return null;
                }
                config.durationMinutes = durationMinutes;
            } else if (arg.equals("-h") || arg.equals("--help")) {
                return null; // Will trigger usage message
            } else if (arg.startsWith("-")) {
                System.err.println("Error: unknown option: " + arg);
                return null;
            } else {
                // Assume it's the puzzle file
                if (config.puzzleFile != null) {
                    System.err.println("Error: multiple puzzle files specified");
                    return null;
                }
                config.puzzleFile = arg;
            }
        }

        // Validate that puzzle file was provided
        if (config.puzzleFile == null) {
            System.err.println("Error: no puzzle file specified");
            return null;
        }

        return config;
    }

    /**
     * Parses a duration string with optional time unit suffix.
     * Supported units: s (seconds), m (minutes, default), h (hours), d (days), w (weeks).
     * Examples: "30" = 30 minutes, "30h" = 30 hours, "5d" = 5 days.
     *
     * @param durationStr the duration string to parse
     * @return duration in minutes, or -1 if parsing fails
     */
    private static int parseDuration(String durationStr) {
        if (durationStr == null || durationStr.isEmpty()) {
            return -1;
        }

        // Find where the number ends and the unit begins
        int unitStart = durationStr.length();
        for (int i = 0; i < durationStr.length(); i++) {
            char c = durationStr.charAt(i);
            if (!Character.isDigit(c)) {
                unitStart = i;
                break;
            }
        }

        // Parse the numeric value
        String numberPart = durationStr.substring(0, unitStart);
        String unitPart = durationStr.substring(unitStart).toLowerCase();

        if (numberPart.isEmpty()) {
            return -1;
        }

        int value;
        try {
            value = Integer.parseInt(numberPart);
        } catch (NumberFormatException e) {
            return -1;
        }

        if (value < 0) {
            return -1;
        }

        // Convert to minutes based on unit
        if (unitPart.isEmpty() || unitPart.equals("m")) {
            // Minutes (default)
            return value;
        } else if (unitPart.equals("s")) {
            // Seconds - convert to minutes (round up if non-zero remainder)
            return (value + 59) / 60;
        } else if (unitPart.equals("h")) {
            // Hours
            return value * 60;
        } else if (unitPart.equals("d")) {
            // Days
            return value * 60 * 24;
        } else if (unitPart.equals("w")) {
            // Weeks
            return value * 60 * 24 * 7;
        } else {
            // Unknown unit
            return -1;
        }
    }

    /**
     * Prints usage information.
     */
    private static void printUsage() {
        System.out.println("Harmony Puzzle Solver");
        System.out.println();
        System.out.println("Usage: java -jar harmony-solver.jar [options] <puzzle-file>");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -t, --threads <N>     Number of worker threads (default: 2)");
        System.out.println("  -r, --report <N>      Progress report interval in seconds (default: 30, 0 to disable)");
        System.out.println("  -c, --cache <N>       Cache threshold: states with N+ moves taken are cached locally (default: 4)");
        System.out.println("  -repl <N>             Replication factor for queue distribution (default: 3)");
        System.out.println("  -dur, --duration <N>  Run duration with optional unit suffix (default: 120m)");
        System.out.println("                        Units: s (seconds), m (minutes), h (hours), d (days), w (weeks)");
        System.out.println("                        Examples: 30 (30 min), 30h (30 hours), 5d (5 days)");
        System.out.println("  -d, --debug           Debug mode: disable empty queue termination (for breakpoints)");
        System.out.println("  -i, --invalidity      Show invalidity test statistics instead of queue sizes");
        System.out.println("  --smallestFirst       Process moves with smallest tile sum first");
        System.out.println("  --largestFirst        Process moves with largest tile sum first");
        System.out.println("  -h, --help            Show this help message");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java -jar harmony-solver.jar -t 8 -r 10 -c 6 puzzle.txt");
    }

    /**
     * Prints application header.
     */
    private static void printHeader(Config config) {
        System.out.println("=".repeat(80));
        System.out.println("Harmony Puzzle Solver");
        System.out.println("Multi-threaded depth-first search with intelligent pruning");
        System.out.println("=".repeat(80));
        System.out.println("Configuration:");
        System.out.println("  Threads: " + config.threadCount);
        System.out.println("  Report interval: " + (config.reportInterval > 0 ? config.reportInterval + " seconds" : "disabled"));
        System.out.println("  Cache threshold: " + config.cacheThreshold + " moves taken");
        System.out.println("  Replication factor: " + config.replicationFactor);
        System.out.println("  Duration: " + config.durationMinutes + " minutes");
        System.out.println("  Invalidity tests: " +
            InvalidityTestCoordinator.getInstance().getTestCount());
        if (config.invalidityStats) {
            System.out.println("  Invalidity stats: enabled");
        }
        if (config.debugMode) {
            System.out.println("  DEBUG MODE: Empty queue termination disabled");
        }
        System.out.println("=".repeat(80));
        System.out.println();
    }

    /**
     * Configuration holder.
     */
    private static class Config {
        String puzzleFile;
        int threadCount = DEFAULT_THREAD_COUNT;
        int reportInterval = DEFAULT_REPORT_INTERVAL;
        int cacheThreshold = DEFAULT_CACHE_THRESHOLD;
        boolean debugMode = DEFAULT_DEBUG_MODE;
        boolean invalidityStats = false;
        SortMode sortMode = SortMode.NONE;
        int replicationFactor = DEFAULT_REPLICATION_FACTOR;
        int durationMinutes = DEFAULT_DURATION_MINUTES;
    }
}
