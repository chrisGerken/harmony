package org.gerken.harmony;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Main class for the Harmony Puzzle Solver.
 * A multi-threaded solver for color-matching tile puzzles using breadth-first search with pruning.
 */
public class HarmonySolver {

    private static final int DEFAULT_THREAD_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int DEFAULT_REPORT_INTERVAL = 30; // seconds

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
            System.out.println();

            // Check if already solved
            if (initialState.isSolved()) {
                System.out.println("Puzzle is already solved!");
                System.exit(0);
            }

            // Solve the puzzle
            BoardState solution = solve(initialState, config);

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
     * Solves the puzzle using multi-threaded breadth-first search with pruning.
     *
     * @param initialState the starting board state
     * @param config solver configuration
     * @return the solution state, or null if no solution found
     */
    private static BoardState solve(BoardState initialState, Config config) {
        // Initialize shared data structures
        ConcurrentLinkedQueue<BoardState> queue = new ConcurrentLinkedQueue<>();
        AtomicBoolean solutionFound = new AtomicBoolean(false);
        AtomicReference<BoardState> solution = new AtomicReference<>(null);
        AtomicLong statesProcessed = new AtomicLong(0);
        AtomicLong statesGenerated = new AtomicLong(0);
        AtomicLong statesPruned = new AtomicLong(0);

        // Add initial state to queue
        queue.add(initialState);

        // Create worker threads
        ExecutorService workerPool = Executors.newFixedThreadPool(config.threadCount);
        List<StateProcessor> processors = new ArrayList<>();

        System.out.println("Starting " + config.threadCount + " worker threads...");
        for (int i = 0; i < config.threadCount; i++) {
            StateProcessor processor = new StateProcessor(
                queue, solutionFound, solution,
                statesProcessed, statesGenerated, statesPruned
            );
            processors.add(processor);
            workerPool.submit(processor);
        }

        // Create and start progress reporter thread
        ProgressReporter reporter = new ProgressReporter(
            queue, solutionFound,
            statesProcessed, statesGenerated, statesPruned,
            config.reportInterval
        );
        Thread reporterThread = new Thread(reporter);
        reporterThread.setDaemon(true);
        reporterThread.start();

        System.out.println("Search started...\n");

        // Wait for solution or queue exhaustion
        try {
            // Check periodically if we should stop
            while (!solutionFound.get()) {
                Thread.sleep(1000);

                // Check if queue is empty and no more work will be generated
                if (queue.isEmpty() && statesProcessed.get() == statesGenerated.get()) {
                    // All generated states have been processed
                    // If we haven't found a solution, puzzle is unsolvable
                    break;
                }
            }

            // Shutdown worker threads
            solutionFound.set(true); // Signal threads to stop
            workerPool.shutdown();
            workerPool.awaitTermination(5, TimeUnit.SECONDS);

            // Print final summary
            reporter.printFinalSummary(solution.get() != null);

            return solution.get();

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
        System.out.println("  -t, --threads <N>     Number of worker threads (default: CPU cores)");
        System.out.println("  -r, --report <N>      Progress report interval in seconds (default: 30)");
        System.out.println("  -h, --help            Show this help message");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java -jar harmony-solver.jar -t 8 -r 10 puzzle.txt");
    }

    /**
     * Prints application header.
     */
    private static void printHeader(Config config) {
        System.out.println("=".repeat(80));
        System.out.println("Harmony Puzzle Solver");
        System.out.println("Multi-threaded breadth-first search with intelligent pruning");
        System.out.println("=".repeat(80));
        System.out.println("Configuration:");
        System.out.println("  Threads: " + config.threadCount);
        System.out.println("  Report interval: " + config.reportInterval + " seconds");
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
    }
}
