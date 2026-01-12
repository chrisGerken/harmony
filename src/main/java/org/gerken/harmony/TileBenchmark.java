package org.gerken.harmony;

import org.gerken.harmony.model.Tile;

/**
 * Benchmark comparing Tile.copy() vs array clone() performance.
 */
public class TileBenchmark {

    private static final int ARRAY_SIZE = 1_000_000;
    private static final int WARMUP_ITERATIONS = 5;
    private static final int TEST_ITERATIONS = 10;

    public static void main(String[] args) {
        System.out.println("Tile Copy Benchmark");
        System.out.println("===================");
        System.out.println("Array size: " + ARRAY_SIZE);
        System.out.println();

        // Create source array
        Tile[] source = new Tile[ARRAY_SIZE];
        for (int i = 0; i < ARRAY_SIZE; i++) {
            source[i] = new Tile(i % 10, i % 5);
        }

        // Warmup
        System.out.println("Warming up...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            copyWithClone(source);
            copyWithCopyMethod(source);
        }

        // Benchmark clone()
        System.out.println("Testing array clone()...");
        long cloneTotal = 0;
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            Tile[] result = copyWithClone(source);
            long end = System.nanoTime();
            cloneTotal += (end - start);
            // Prevent optimization
            if (result[0] == null) System.out.println("null");
        }
        double cloneAvgMs = (cloneTotal / TEST_ITERATIONS) / 1_000_000.0;

        // Benchmark copy()
        System.out.println("Testing Tile.copy()...");
        long copyTotal = 0;
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            Tile[] result = copyWithCopyMethod(source);
            long end = System.nanoTime();
            copyTotal += (end - start);
            // Prevent optimization
            if (result[0] == null) System.out.println("null");
        }
        double copyAvgMs = (copyTotal / TEST_ITERATIONS) / 1_000_000.0;

        // Results
        System.out.println();
        System.out.println("Results:");
        System.out.println("========");
        System.out.printf("Array clone():  %.3f ms%n", cloneAvgMs);
        System.out.printf("Tile.copy():    %.3f ms%n", copyAvgMs);
        System.out.println();

        if (cloneAvgMs < copyAvgMs) {
            System.out.printf("clone() is %.2fx faster%n", copyAvgMs / cloneAvgMs);
            System.out.println("Recommendation: Keep using clone()");
        } else {
            System.out.printf("copy() is %.2fx faster%n", cloneAvgMs / copyAvgMs);
            System.out.println("Recommendation: Replace clone() with copy()");
        }
    }

    /**
     * Copy using array clone() - copies references (shallow copy).
     * Since Tile is immutable, this is effectively a deep copy.
     */
    private static Tile[] copyWithClone(Tile[] source) {
        return source.clone();
    }

    /**
     * Copy using Tile.copy() - creates new Tile objects.
     */
    private static Tile[] copyWithCopyMethod(Tile[] source) {
        Tile[] result = new Tile[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i].copy();
        }
        return result;
    }
}
