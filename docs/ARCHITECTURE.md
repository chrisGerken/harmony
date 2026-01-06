# System Architecture

[← Back to README](../README.md)

## Table of Contents

- [Overview](#overview)
- [High-Level Design](#high-level-design)
- [Core Algorithm](#core-algorithm)
- [Concurrency Model](#concurrency-model)
- [State Management](#state-management)
- [Pruning Strategy](#pruning-strategy)
- [Progress Reporting](#progress-reporting)
- [Filesystem Offloading](#filesystem-offloading)

## Overview

The Harmony Puzzle Solver is built around a parallel breadth-first search (BFS) algorithm with intelligent pruning. The system explores the state space of possible board configurations, searching for a solution that satisfies the win conditions.

### Design Goals

1. **Correctness**: Find valid solutions when they exist
2. **Performance**: Leverage multi-core processors for parallel exploration
3. **Scalability**: Handle large state spaces through pruning and offloading
4. **Maintainability**: Clean separation of concerns, extensible design

## High-Level Design

```
┌─────────────────────────────────────────────────────────────┐
│                      Main Application                        │
│  - Parse input file (DSL)                                   │
│  - Initialize original board state                          │
│  - Configure thread pool                                    │
│  - Start worker threads                                     │
│  - Monitor progress                                         │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
        ┌─────────────────────────────────────────┐
        │     ConcurrentLinkedQueue<BoardState>    │
        │          (Pending States)                │
        └─────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
    ┌──────────┐        ┌──────────┐        ┌──────────┐
    │ Worker 1 │        │ Worker 2 │   ...  │ Worker N │
    │ Thread   │        │ Thread   │        │ Thread   │
    └──────────┘        └──────────┘        └──────────┘
          │                   │                   │
          └───────────────────┼───────────────────┘
                              ▼
              ┌───────────────────────────────┐
              │ InvalidityTestCoordinator     │
              │  - Run all pruning tests      │
              └───────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
    ┌──────────┐      ┌──────────────┐    ┌──────────────┐
    │Too Many  │      │Impossible    │    │Insufficient  │
    │Moves Test│      │Color Test    │    │Moves Test    │
    └──────────┘      └──────────────┘    └──────────────┘
```

## Core Algorithm

### Main Loop

```
1. Read original board from input file
2. Create initial BoardState with no moves
3. Add initial state to pending queue
4. Start N worker threads
5. Each worker continuously:
   a. Poll a BoardState from the queue
   b. If queue is empty, wait or exit
   c. If board is solved, output solution and terminate all threads
   d. Generate all possible moves from this state
   e. For each move:
      - Create new BoardState
      - Check if invalid using InvalidityTestCoordinator
      - If valid and not solved, add to pending queue
      - If solved, output solution and terminate
6. Continue until solution found or queue exhausted
```

### State Space Exploration

The solver explores the state space as a tree:

```
                    Original Board
                   /      |      \
              Move 1    Move 2   Move 3
              /  |  \   /  |  \   /  |  \
           ...  ...  ... ... ... ... ... ...
```

Each node represents a `BoardState`, and edges represent `Move` operations.

## Concurrency Model

### Thread-Safe Components

1. **ConcurrentLinkedQueue**: Thread-safe FIFO queue for pending states
   - Multiple producers (workers generating new states)
   - Multiple consumers (workers polling next state to process)

2. **InvalidityTest Singletons**: Thread-safe, stateless validators
   - Eagerly initialized singletons
   - No mutable state
   - Safe for concurrent access

3. **Immutable Data Structures**: `Tile`, `Board`, `BoardState`, `Move`
   - All state changes create new objects
   - No synchronization needed

### Worker Thread Pattern

Each worker is a `Runnable` that:
1. Polls from the shared queue
2. Processes the state independently
3. Adds new states back to the queue
4. No shared mutable state between workers

### Configuration

- **Thread Count**: Configurable via command-line argument
- **Recommended**: Number of CPU cores or cores - 1
- **Default**: Runtime.getRuntime().availableProcessors()

## State Management

### Queue Operations

```java
// Worker pseudo-code
while (running) {
    BoardState state = pendingQueue.poll();
    if (state == null) {
        // Queue empty - wait or exit
        continue;
    }

    processState(state);
}
```

### Memory Management

- Queue grows as new states are discovered
- Pruning reduces queue growth
- Filesystem offloading prevents OOM errors
- Solved/invalid states are discarded immediately

## Pruning Strategy

### Why Pruning Matters

Without pruning, the state space grows exponentially:
- Board with N positions and M tiles: O(M^N) possible configurations
- Typical 4x4 board: ~10^19 possible states
- Pruning reduces this by orders of magnitude

### Pruning Architecture

All pruning logic is encapsulated in `InvalidityTest` implementations:

```java
public interface InvalidityTest {
    boolean isInvalid(BoardState boardState);
    String getName();
}
```

### Current Tests

See [Invalidity Tests](INVALIDITY_TESTS.md) for detailed documentation.

1. **TooManyMovesTest**: Tiles with impossible move counts
2. **ImpossibleColorAlignmentTest**: Insufficient tiles for color requirements
3. **InsufficientMovesTest**: Tiles stuck in wrong positions

### Adding New Tests

1. Implement `InvalidityTest` interface
2. Use singleton pattern
3. Ensure thread-safety (stateless preferred)
4. Register in `InvalidityTestCoordinator` constructor
5. Test thoroughly - incorrect pruning can eliminate valid solutions!

## Progress Reporting

### Requirements

- Report every 30 seconds (configurable)
- Show states processed, queue size, estimated time remaining
- Non-blocking (doesn't slow down workers)

### Implementation Approach

Separate progress reporter thread:

```java
class ProgressReporter implements Runnable {
    void run() {
        while (running) {
            Thread.sleep(reportIntervalMs);
            printProgress();
        }
    }
}
```

### Metrics to Track

- **States Processed**: Atomic counter incremented by workers
- **Queue Size**: pendingQueue.size()
- **Start Time**: Record at beginning
- **Estimated Completion**: Based on processing rate

## Filesystem Offloading

### Trigger Condition

When queue size exceeds threshold:
```java
if (pendingQueue.size() > OFFLOAD_THRESHOLD) {
    offloadToFileSystem();
}
```

### Implementation Strategy

1. **Serialization**: Serialize BoardState objects
   - Use Java serialization or custom format
   - Store move sequences (compact representation)

2. **Storage**: Write to temporary files
   - One file per batch of states
   - Delete after reloading

3. **Reloading**: Background thread loads states back
   - Monitor queue size
   - Reload when queue drops below threshold

### Trade-offs

- **Pro**: Prevents OOM errors, handles unlimited state spaces
- **Con**: I/O overhead, slower than in-memory
- **Optimization**: Only offload when necessary

## Performance Considerations

### Bottlenecks

1. **Queue Contention**: Many threads accessing same queue
   - Mitigation: `ConcurrentLinkedQueue` is lock-free

2. **Duplicate States**: Processing same board multiple times
   - Future: Add state deduplication (hash set of seen states)

3. **Pruning Overhead**: Running tests on every state
   - Mitigation: Order tests by speed (fast tests first)
   - Early exit on first invalid test

### Optimization Opportunities

1. **Work Stealing**: Better load balancing between threads
2. **State Deduplication**: Avoid processing duplicate boards
3. **Heuristic Ordering**: Process promising states first (A* search)
4. **Parallel Pruning**: Run tests in parallel (if beneficial)

## Related Documentation

- [Data Models](DATA_MODELS.md) - Core classes and data structures
- [Invalidity Tests](INVALIDITY_TESTS.md) - Pruning system details
- [Development Guide](DEVELOPMENT.md) - Implementation guidelines

[← Back to README](../README.md)
