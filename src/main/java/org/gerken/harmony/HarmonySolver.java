package org.gerken.harmony;

import org.gerken.harmony.invalidity.InvalidityTestCoordinator;
import org.gerken.harmony.logic.BoardParser;
import org.gerken.harmony.logic.PendingStates;
import org.gerken.harmony.logic.ProgressReporter;
import org.gerken.harmony.logic.StateProcessor;
import org.gerken.harmony.model.BoardState;
import org.gerken.harmony.model.Move;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    // Instance fields for solver state
    private final Config config;
    private PendingStates pendingStates;
    private List<StateProcessor> processors;
    private int initialRemainingMoves;

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
                System.exit(0);
            }

            // Create solver instance and solve the puzzle
            HarmonySolver solver = new HarmonySolver(config);
            BoardState solution = solver.solve(initialState);

            // Print results
            if (solution != null) {
                printSolution(solution);
                System.exit(0);
            } else {
                System.out.println("No solution found. The puzzle may be unsolvable.");
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
     * @return the solution state, or null if no solution found
     */
    private BoardState solve(BoardState initialState) {
        // Initialize shared data structures
        this.pendingStates = new PendingStates();
        this.initialRemainingMoves = initialState.getRemainingMoves();

        // Add initial state to queue
        pendingStates.add(initialState);

        // Create worker threads
        ExecutorService workerPool = Executors.newFixedThreadPool(config.threadCount);
        this.processors = new ArrayList<>();

        System.out.println("Starting " + config.threadCount + " worker threads...");
        for (int i = 0; i < config.threadCount; i++) {
            StateProcessor processor = new StateProcessor(pendingStates, config.cacheThreshold);
            processors.add(processor);
            workerPool.submit(processor);
        }

        // Create and start progress reporter thread
        ProgressReporter reporter = new ProgressReporter(this);
        Thread reporterThread = new Thread(reporter);
        reporterThread.setDaemon(true);
        reporterThread.start();

        System.out.println("Search started...\n");

        // Wait for solution or queue exhaustion
        try {
            // Check periodically if we should stop
            while (!pendingStates.isSolutionFound()) {
                Thread.sleep(1000);

                // Check if queue is empty and no more work will be generated
                if (pendingStates.isEmpty() &&
                    pendingStates.getStatesProcessed() == pendingStates.getStatesGenerated()) {
                    // All generated states have been processed
                    // If we haven't found a solution, puzzle is unsolvable
                    break;
                }
            }

            // Shutdown worker threads
            pendingStates.setSolutionFound(); // Signal threads to stop
            workerPool.shutdown();
            workerPool.awaitTermination(5, TimeUnit.SECONDS);

            // Print final summary
            reporter.printFinalSummary(pendingStates.getSolution() != null);

            return pendingStates.getSolution();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            workerPool.shutdownNow();
            return null;
        }
    }

    /**
     * Prints the solution (sequence of moves).
     */
    private static void printSolution(BoardState solution) {
        List<Move> moves = solution.getMoves();

        System.out.println("\nSOLUTION FOUND!");
        System.out.println("Number of moves: " + moves.size());
        System.out.println("\nMove sequence:");

        for (int i = 0; i < moves.size(); i++) {
            System.out.printf("%3d. %s%n", i + 1, moves.get(i).getNotation());
        }
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
                    if (config.reportInterval < 1) {
                        System.err.println("Error: report interval must be positive");
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
     * Prints usage information.
     */
    private static void printUsage() {
        System.out.println("Harmony Puzzle Solver");
        System.out.println();
        System.out.println("Usage: java -jar harmony-solver.jar [options] <puzzle-file>");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -t, --threads <N>     Number of worker threads (default: 2)");
        System.out.println("  -r, --report <N>      Progress report interval in seconds (default: 30)");
        System.out.println("  -c, --cache <N>       Cache threshold for near-solution states (default: 4)");
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
        System.out.println("  Report interval: " + config.reportInterval + " seconds");
        System.out.println("  Cache threshold: " + config.cacheThreshold + " moves");
        System.out.println("  Invalidity tests: " +
            InvalidityTestCoordinator.getInstance().getTestCount());
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
    }
}
