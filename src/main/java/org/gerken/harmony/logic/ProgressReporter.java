package org.gerken.harmony.logic;

import org.gerken.harmony.HarmonySolver;

/**
 * Periodically reports progress during the solving process.
 * Runs as a background thread and outputs statistics at configurable intervals.
 */
public class ProgressReporter implements Runnable {

    private final HarmonySolver solver;
    private final long reportIntervalMs;
    private final long startTime;

    /**
     * Creates a new progress reporter.
     *
     * @param solver the solver instance to report progress for
     */
    public ProgressReporter(HarmonySolver solver) {
        this.solver = solver;
        this.reportIntervalMs = solver.getReportInterval() * 1000L;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public void run() {
        try {
            PendingStates pendingStates = solver.getPendingStates();
            while (!pendingStates.isSolutionFound()) {
                Thread.sleep(reportIntervalMs);

                // Don't report if solution was found during sleep
                if (pendingStates.isSolutionFound()) {
                	System.out.println("Reporter sees that a solution is found. Stopping");
                    break;
                }

                printProgress();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Prints the current progress statistics.
     */
    private void printProgress() {
        if (solver.isInvalidityStats()) {
            printInvalidityStats();
        } else {
            printQueueStats();
        }
    }

    /**
     * Prints the standard progress statistics with queue sizes.
     */
    private void printQueueStats() {
        PendingStates pendingStates = solver.getPendingStates();
        long processed = pendingStates.getStatesProcessed();
        long generated = pendingStates.getStatesGenerated();
        long pruned = pendingStates.getStatesPruned();
        int queueSize = pendingStates.size();
        long elapsedMs = System.currentTimeMillis() - startTime;

        // Add cache sizes from all processors to queue size and calculate average processing time
        int totalCacheSize = 0;
        double totalAvgTimeMs = 0.0;
        int processorCount = 0;
        for (StateProcessor processor : solver.getProcessors()) {
            totalCacheSize += processor.getCacheSize();
            double avgTime = processor.getAverageProcessingTimeMs();
            if (avgTime > 0) {
                totalAvgTimeMs += avgTime;
                processorCount++;
            }
        }
        int totalQueueSize = queueSize + totalCacheSize;
        double overallAvgTimeMs = processorCount > 0 ? totalAvgTimeMs / processorCount : 0.0;

        // Get progress info: all queues from first non-empty to last non-empty
        int[][] queueRangeInfo = pendingStates.getQueueRangeInfo();
        String progressStr;
        if (queueRangeInfo != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < queueRangeInfo.length; i++) {
                if (i > 0) {
                    sb.append(" ");
                }
                sb.append(queueRangeInfo[i][0]).append(":").append(queueRangeInfo[i][1]);
            }
            progressStr = sb.toString();
        } else {
            progressStr = "empty";
        }

        // Calculate statistics
        double elapsedSeconds = elapsedMs / 1000.0;
        double statesPerSecond = processed / elapsedSeconds;
        double pruneRate = generated > 0 ? (100.0 * pruned / generated) : 0.0;

        // Format and print progress
        System.out.printf(
            "[%s] Processed: %s | Pruned: %.1f%% | Queues: %s | " +
            "Rate: %s/s | Avg: %.3fms%n",
            formatDuration(elapsedSeconds),
            formatCount(processed),
            pruneRate,
            progressStr,
            formatCount((long) statesPerSecond),
            overallAvgTimeMs
        );
    }

    /**
     * Prints invalidity test statistics as a table.
     * Rows are invalidity tests, columns are move counts.
     */
    private void printInvalidityStats() {
        PendingStates pendingStates = solver.getPendingStates();
        long elapsedMs = System.currentTimeMillis() - startTime;
        double elapsedSeconds = elapsedMs / 1000.0;

        int maxMoves = pendingStates.getMaxInvalidityMoveCount();
        if (maxMoves < 0) {
            System.out.printf("[%s] No invalidity data yet%n", formatDuration(elapsedSeconds));
            return;
        }

        // Test names in display order (matches coordinator order)
        String[] testNames = {"BlockedSwapTest", "StuckTilesTest", "IsolatedTileTest",
                              "StalemateTest", "WrongRowZeroMovesTest"};
        String[] shortNames = {"BlockedSwap", "StuckTiles", "IsolatedTile",
                               "Stalemate", "WrongRowZero"};

        // Print header with blank line before for readability
        System.out.printf("%n%n[%s] Invalidity Statistics:%n", formatDuration(elapsedSeconds));
        System.out.printf("%-14s", "Test");
        for (int m = 1; m <= maxMoves; m++) {
            System.out.printf("%8d", m);
        }
        System.out.println();

        // Print separator
        System.out.print("-".repeat(14));
        for (int m = 1; m <= maxMoves; m++) {
            System.out.print("-".repeat(8));
        }
        System.out.println();

        // Print each test row
        for (int t = 0; t < testNames.length; t++) {
            System.out.printf("%-14s", shortNames[t]);
            for (int m = 1; m <= maxMoves; m++) {
                long count = pendingStates.getInvalidityCount(m, testNames[t]);
                System.out.printf("%8s", formatCount(count));
            }
            System.out.println();
        }
        System.out.println();
    }

    /**
     * Formats a count with compact suffixes (T, B, M, K) with exactly one decimal place.
     * Examples: 123456789 → "123.5M", 5432 → "5.4K", 123 → "123"
     *
     * @param count the count to format
     * @return formatted string with suffix
     */
    private String formatCount(long count) {
        if (count >= 1_000_000_000_000L) {
            return String.format("%.1fT", count / 1_000_000_000_000.0);
        } else if (count >= 1_000_000_000L) {
            return String.format("%.1fB", count / 1_000_000_000.0);
        } else if (count >= 1_000_000L) {
            return String.format("%.1fM", count / 1_000_000.0);
        } else if (count >= 1_000L) {
            return String.format("%.1fK", count / 1_000.0);
        } else {
            return String.valueOf(count);
        }
    }

    /**
     * Formats a duration in seconds to a fixed-width string.
     *
     * @param seconds the duration in seconds
     * @return formatted string (e.g., "001:23:45")
     */
    private String formatDuration(double seconds) {
        int totalSeconds = (int) seconds;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int secs = totalSeconds % 60;
        return String.format("%03d:%02d:%02d", hours, minutes, secs);
    }

    /**
     * Prints a final summary when solving is complete.
     */
    public void printFinalSummary(boolean foundSolution) {
        PendingStates pendingStates = solver.getPendingStates();
        long processed = pendingStates.getStatesProcessed();
        long generated = pendingStates.getStatesGenerated();
        long pruned = pendingStates.getStatesPruned();
        long elapsedMs = System.currentTimeMillis() - startTime;
        double elapsedSeconds = elapsedMs / 1000.0;
        double statesPerSecond = processed / elapsedSeconds;
        double pruneRate = generated > 0 ? (100.0 * pruned / generated) : 0.0;

        System.out.println("\n" + "=".repeat(80));
        System.out.println(foundSolution ? "SOLUTION FOUND!" : "Search complete");
        System.out.println("=".repeat(80));
        System.out.printf("Total time:        %s%n", formatDuration(elapsedSeconds));
        System.out.printf("States processed:  %s%n", formatCount(processed));
        System.out.printf("States generated:  %s%n", formatCount(generated));
        System.out.printf("States pruned:     %s (%.1f%%)%n", formatCount(pruned), pruneRate);
        System.out.printf("Processing rate:   %s/second%n", formatCount((long) statesPerSecond));
        System.out.println("=".repeat(80));
    }
}
