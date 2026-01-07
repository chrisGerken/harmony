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

The Harmony Puzzle Solver is built around a parallel depth-first search (DFS) algorithm with intelligent pruning. The system explores the state space of possible board configurations, searching for a solution that satisfies the win conditions.

The solver uses a novel multi-queue depth-first approach where states are organized by move depth, always processing the deepest states first. This prevents the queue explosion problem typical of breadth-first search while maintaining the benefits of parallel processing.

### Design Goals

1. **Correctness**: Find valid solutions when they exist
2. **Performance**: Leverage multi-core processors for parallel exploration
3. **Memory Efficiency**: Prevent queue explosion through depth-first strategy
4. **Scalability**: Handle large state spaces through pruning and depth-first traversal
5. **Maintainability**: Clean separation of concerns, extensible design

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
        ┌─────────────────────────────────────────────────────┐
        │             PendingStates Container                  │
        │  - Multiple queues by move depth (DFS)              │
        │  - Statistics counters                              │
        │  - Solution coordination                            │
        │                                                     │
        │  Depth queues:                                      │
        │    Move 0: Queue<BoardState>                       │
        │    Move 1: Queue<BoardState>                       │
        │    Move 2: Queue<BoardState>                       │
        │    ...                                              │
        │    Move N: Queue<BoardState> ← Poll from here first│
        └─────────────────────────────────────────────────────┘
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
    │StuckTile │      │WrongRowZero  │    │BlockedSwap   │
    │Test      │      │MovesTest     │    │Test          │
    └──────────┘      └──────────────┘    └──────────────┘
```

## Core Algorithm

### Main Loop

```
1. Read original board from input file
2. Create initial BoardState with no moves
3. Create PendingStates container
4. Add initial state to PendingStates (goes to depth-0 queue)
5. Start N worker threads
6. Each worker continuously:
   a. Poll a BoardState from PendingStates (gets from deepest queue)
   b. If queue is empty, wait or exit
   c. If board is solved, mark solution and terminate all threads
   d. Generate all possible moves from this state
   e. For each move:
      - Create new BoardState
      - Check if invalid using InvalidityTestCoordinator
      - If valid and not solved, add to PendingStates (routed by depth)
      - If solved, mark solution and terminate
7. Continue until solution found or all queues exhausted
```

### Depth-First State Space Exploration

The solver explores the state space depth-first using multiple queues:

```
                    Original Board (depth 0)
                          │
                    [Queue depth-0]
                          │
                   /      |      \
              Move 1    Move 2   Move 3 (depth 1)
                   \     |      /
                    [Queue depth-1]
                          │
              /     |     |     |     \
           M2-M1  M2-M2  M2-M3 ...   ... (depth 2)
                          │
                    [Queue depth-2] ← Always poll from deepest first
                          │
                        ...
```

**Key Insight**: By always processing from the deepest available queue, we:
- Quickly reach invalid states and prune them
- Avoid accumulating shallow states
- Keep memory usage low
- Often find solutions faster

Each node represents a `BoardState`, edges represent `Move` operations.
States are automatically routed to the appropriate depth queue.

## State Management

### PendingStates Container

The `PendingStates` class is the central coordination point for all state management:

```java
public class PendingStates {
    // Depth-organized queues
    private ConcurrentHashMap<Integer, ConcurrentLinkedQueue<BoardState>> queuesByMoveCount;
    private AtomicInteger maxMoveCount;

    // Solution coordination
    private AtomicBoolean solutionFound;
    private AtomicReference<BoardState> solution;

    // Statistics
    private AtomicLong statesProcessed;
    private AtomicLong statesGenerated;
    private AtomicLong statesPruned;
}
```

**Key Operations**:

1. **add(BoardState)**: Routes states to appropriate depth queue
   - Extracts move count from state
   - Updates max depth if necessary
   - Creates queue on-demand if needed
   - Thread-safe through `computeIfAbsent()`

2. **poll()**: Retrieves from deepest queue first
   - Starts at `maxMoveCount` and works backward
   - Returns first non-null state found
   - Returns null only when all queues empty
   - O(depth) complexity, but depth typically small

3. **Statistics**: Encapsulates all counters
   - Processed/generated/pruned counts
   - Solution found flag and state
   - Thread-safe atomic operations

**Design Benefits**:
- Single source of truth for state management
- Depth-first traversal guaranteed
- Clean API hides complexity
- Easy to extend with new features

## Concurrency Model

### Thread-Safe Components

1. **PendingStates Container**: Encapsulates all state coordination
   - Multiple concurrent queues (one per depth level)
   - Thread-safe routing and retrieval
   - Atomic counters and solution coordination
   - Multiple producers (workers generating new states)
   - Multiple consumers (workers polling next state to process)

2. **ConcurrentHashMap**: Maps depth → queue
   - Thread-safe queue creation via `computeIfAbsent()`
   - Multiple threads can safely access different depth queues
   - No contention for map operations

3. **ConcurrentLinkedQueue**: One per depth level
   - Lock-free FIFO operations
   - High throughput under contention

4. **InvalidityTest Singletons**: Thread-safe, stateless validators
   - Eagerly initialized singletons
   - No mutable state
   - Safe for concurrent access

5. **Immutable Data Structures**: `Tile`, `Board`, `BoardState`, `Move`
   - All state changes create new objects
   - No synchronization needed

### Worker Thread Pattern

Each worker is a `Runnable` that:
1. Polls from PendingStates (gets from deepest queue)
2. Processes the state independently
3. Adds new states back to PendingStates (routed by depth)
4. No shared mutable state between workers
5. Coordinates via PendingStates methods

### Configuration

- **Thread Count**: Configurable via command-line argument (`-t <threads>`)
- **Default**: 2 threads
- **Reasoning**: Predictable behavior across machines, resource control

### Queue Operations

```java
// Worker pseudo-code (simplified)
while (!pendingStates.isSolutionFound()) {
    BoardState state = pendingStates.poll(); // Gets from deepest queue
    if (state == null) {
        // All queues empty - wait or exit
        Thread.sleep(100);
        continue;
    }

    if (state.isSolved()) {
        pendingStates.markSolutionFound(state);
        return;
    }

    processState(state); // Generates new states, adds via pendingStates.add()
    pendingStates.incrementStatesProcessed();
}
```

### Memory Management

**Depth-First Advantages**:
- Queue size remains bounded and manageable
- Processes deep states immediately rather than accumulating shallow ones
- Only stores one "branch" of search tree at a time
- Memory usage grows linearly with depth, not exponentially with breadth

**Comparison**:
- **BFS**: Queue size = O(branching_factor^depth) - exponential growth
- **DFS**: Queue size = O(depth × branching_factor) - linear growth

**Example**: For a puzzle with 6 possible moves per state:
- At depth 10 with BFS: ~60 million potential states in queue
- At depth 10 with DFS: ~60 states in queue (10 depths × 6 branches)

**Note**: Actual numbers depend on pruning effectiveness, but DFS consistently uses far less memory
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

1. **StuckTileTest**: Row has all correct colors, all tiles 0 moves except one with 1 move
2. **WrongRowZeroMovesTest**: Tiles with 0 moves stuck in wrong row
3. **BlockedSwapTest**: Tiles with 1 move blocked by 0-move tiles in target position

These tests provide ~60-70% pruning rate without eliminating valid solution paths.

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
