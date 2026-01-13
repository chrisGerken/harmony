package org.gerken.harmony.logic;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Context for distributing board states across replicated queues.
 * Each StateProcessor has its own QueueContext instance to provide
 * random queue selection for add and poll operations.
 */
public class QueueContext {

    private final int replicationFactor;

    /**
     * Creates a new QueueContext with the specified replication factor.
     *
     * @param replicationFactor the number of replicated queues per move count
     */
    public QueueContext(int replicationFactor) {
        this.replicationFactor = replicationFactor;
    }

    /**
     * Returns a random integer between 0 and replicationFactor-1, inclusive.
     * Used to select which replicated queue to use for add or poll operations.
     *
     * @return a random queue index
     */
    public int getRandomQueueIndex() {
        return ThreadLocalRandom.current().nextInt(replicationFactor);
    }
}
