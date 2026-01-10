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
        PendingStates pendingStates = solver.getPendingStates();
        long processed = pendingStates.getStatesProcessed();
        long generated = pendingStates.getStatesGenerated();
        long pruned = pendingStates.getStatesPruned();
        int queueSize = pendingStates.size();
        long elapsedMs = System.currentTimeMillis() - startTime;

        // Add cache sizes from all processors to queue size
        int totalCacheSize = 0;
        for (StateProcessor processor : solver.getProcessors()) {
            totalCacheSize += processor.getCacheSize();
        }
        int totalQueueSize = queueSize + totalCacheSize;

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
            "Rate: %s/s%n",
            formatDuration(elapsedSeconds),
            formatCount(processed),
            pruneRate,
            progressStr,
            formatCount((long) statesPerSecond)
        );
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
     * Formats a duration in seconds to a human-readable string.
     *
     * @param seconds the duration in seconds
     * @return formatted string (e.g., "1h 23m 45s")
     */
    private String formatDuration(double seconds) {
        if (seconds < 60) {
            return String.format("%.0fs", seconds);
        } else if (seconds < 3600) {
            int minutes = (int) (seconds / 60);
            int secs = (int) (seconds % 60);
            return String.format("%dm %ds", minutes, secs);
        } else {
            int hours = (int) (seconds / 3600);
            int minutes = (int) ((seconds % 3600) / 60);
            int secs = (int) (seconds % 60);
            return String.format("%dh %dm %ds", hours, minutes, secs);
        }
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
