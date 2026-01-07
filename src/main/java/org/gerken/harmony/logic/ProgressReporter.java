package org.gerken.harmony.logic;

/**
 * Periodically reports progress during the solving process.
 * Runs as a background thread and outputs statistics at configurable intervals.
 */
public class ProgressReporter implements Runnable {

    private final PendingStates pendingStates;
    private final long reportIntervalMs;
    private final long startTime;

    /**
     * Creates a new progress reporter.
     *
     * @param pendingStates the shared container of pending states and statistics
     * @param reportIntervalSeconds interval between reports (in seconds)
     */
    public ProgressReporter(PendingStates pendingStates, int reportIntervalSeconds) {
        this.pendingStates = pendingStates;
        this.reportIntervalMs = reportIntervalSeconds * 1000L;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public void run() {
        try {
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
        long processed = pendingStates.getStatesProcessed();
        long generated = pendingStates.getStatesGenerated();
        long pruned = pendingStates.getStatesPruned();
        int queueSize = pendingStates.size();
        long elapsedMs = System.currentTimeMillis() - startTime;

        // Calculate statistics
        double elapsedSeconds = elapsedMs / 1000.0;
        double statesPerSecond = processed / elapsedSeconds;
        double pruneRate = generated > 0 ? (100.0 * pruned / generated) : 0.0;

        // Estimate time remaining (rough estimate based on queue size)
        String eta = "unknown";
        if (statesPerSecond > 0 && queueSize > 0) {
            double remainingSeconds = queueSize / statesPerSecond;
            eta = formatDuration(remainingSeconds);
        }

        // Format and print progress
        System.out.printf(
            "[%s] Processed: %,d | Queue: %,d | Generated: %,d | Pruned: %,d (%.1f%%) | " +
            "Rate: %.1f states/s | ETA: %s%n",
            formatDuration(elapsedSeconds),
            processed,
            queueSize,
            generated,
            pruned,
            pruneRate,
            statesPerSecond,
            eta
        );
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
        System.out.printf("States processed:  %,d%n", processed);
        System.out.printf("States generated:  %,d%n", generated);
        System.out.printf("States pruned:     %,d (%.1f%%)%n", pruned, pruneRate);
        System.out.printf("Processing rate:   %.1f states/second%n", statesPerSecond);
        System.out.println("=".repeat(80));
    }
}
