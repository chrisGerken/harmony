# Invalidity Test System

[← Back to README](../README.md)

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [InvalidityTest Interface](#invaliditytest-interface)
- [InvalidityTestCoordinator](#invaliditytestcoordinator)
- [Implemented Tests](#implemented-tests)
- [Adding New Tests](#adding-new-tests)
- [Best Practices](#best-practices)
- [Performance Considerations](#performance-considerations)

## Overview

The invalidity test system is the core pruning mechanism that makes the Harmony Puzzle Solver tractable. Without pruning, the state space grows exponentially and becomes computationally infeasible. This system eliminates invalid board states early, dramatically reducing the search space.

### Purpose

- **Eliminate impossible states**: Board configurations that cannot lead to a solution
- **Reduce search space**: Prevent exponential growth of pending states
- **Maintain correctness**: Never prune states that could lead to valid solutions
- **Enable scalability**: Make large puzzles solvable in reasonable time

### Design Goals

1. **Extensibility**: Easy to add new pruning strategies
2. **Thread-Safety**: Safe for concurrent access by multiple worker threads
3. **Composability**: Multiple tests work together
4. **Performance**: Fast evaluation to avoid becoming a bottleneck

## Architecture

### Component Diagram

```
┌──────────────────────────────────────────────────────────┐
│                  Worker Thread                            │
│  ┌────────────────────────────────────────────────────┐  │
│  │ Process BoardState                                 │  │
│  │   ↓                                                │  │
│  │ Call InvalidityTestCoordinator.isInvalid()        │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────┬───────────────────────────┘
                               │
                               ▼
         ┌─────────────────────────────────────────┐
         │   InvalidityTestCoordinator (Singleton)  │
         │  ┌──────────────────────────────────┐   │
         │  │ List<InvalidityTest> tests       │   │
         │  │                                  │   │
         │  │ boolean isInvalid(BoardState) { │   │
         │  │   for (test : tests) {          │   │
         │  │     if (test.isInvalid(state))  │   │
         │  │       return true;              │   │
         │  │   }                             │   │
         │  │   return false;                 │   │
         │  │ }                               │   │
         │  └──────────────────────────────────┘   │
         └─────────────────┬───────────────────────┘
                           │ delegates to
           ┌───────────────┼───────────────┬─────────────┐
           ▼               ▼               ▼             ▼
    ┌──────────────┐ ┌──────────────┐ ┌──────────┐  ┌─────┐
    │ TooManyMoves │ │ Impossible   │ │Insufficient│ │ ... │
    │ Test         │ │ ColorAlign   │ │MovesTest   │ │     │
    │ (Singleton)  │ │ Test         │ │(Singleton) │ │     │
    └──────────────┘ └──────────────┘ └──────────────┘ └─────┘
```

### Key Characteristics

- **Singleton Pattern**: All test classes are thread-safe singletons
- **Strategy Pattern**: Each test is an independent strategy
- **Early Exit**: Coordinator stops at first positive invalidity result
- **Stateless**: Tests have no mutable state (thread-safe)

## InvalidityTest Interface

**File**: `src/main/java/org/gerken/harmony/InvalidityTest.java`

### Definition

```java
public interface InvalidityTest {
    /**
     * Determines if the given board state is invalid and cannot lead to a solution.
     * This method must be thread-safe.
     */
    boolean isInvalid(BoardState boardState);

    /**
     * Returns a descriptive name for this invalidity test.
     * Useful for logging and debugging.
     */
    String getName();
}
```

### Contract

#### isInvalid()

- **Input**: A `BoardState` to evaluate
- **Output**: `true` if the state is definitely invalid, `false` otherwise
- **Requirements**:
  - Must be **thread-safe** (no mutable state)
  - Must be **fast** (called on every generated state)
  - Must be **conservative** (false positives eliminate valid solutions!)

#### getName()

- **Returns**: Human-readable test name
- **Purpose**: Debugging, logging, statistics

### Thread Safety Requirements

Since tests are called concurrently by multiple threads:

✅ **Safe**: Stateless tests that only read from `BoardState`
✅ **Safe**: Tests using only local variables
❌ **Unsafe**: Tests with mutable instance fields
❌ **Unsafe**: Tests that modify shared state

## InvalidityTestCoordinator

**File**: `src/main/java/org/gerken/harmony/InvalidityTestCoordinator.java`

### Purpose

Central coordinator that:
1. Registers all `InvalidityTest` implementations
2. Runs all tests against a given `BoardState`
3. Returns `true` if **any** test determines the state is invalid

### Implementation

```java
public class InvalidityTestCoordinator {
    private static final InvalidityTestCoordinator INSTANCE =
        new InvalidityTestCoordinator();

    private final List<InvalidityTest> tests;

    private InvalidityTestCoordinator() {
        List<InvalidityTest> testList = new ArrayList<>();

        // Register all tests
        testList.add(TooManyMovesTest.getInstance());
        testList.add(ImpossibleColorAlignmentTest.getInstance());
        testList.add(InsufficientMovesTest.getInstance());

        this.tests = Collections.unmodifiableList(testList);
    }

    public static InvalidityTestCoordinator getInstance() {
        return INSTANCE;
    }

    public boolean isInvalid(BoardState boardState) {
        for (InvalidityTest test : tests) {
            if (test.isInvalid(boardState)) {
                return true;  // Early exit
            }
        }
        return false;
    }
}
```

### Usage

```java
BoardState state = /* ... */;

if (InvalidityTestCoordinator.getInstance().isInvalid(state)) {
    // Prune this state - don't add to queue
} else {
    // Valid state - continue processing
    pendingQueue.add(state);
}
```

### Design Notes

- **Singleton**: Thread-safe, eagerly initialized
- **Unmodifiable List**: Tests cannot be added/removed at runtime
- **Early Exit**: Stops at first test that returns `true`
- **Ordering**: Tests run in registration order (optimize by putting fastest first)

## Implemented Tests

### 1. TooManyMovesTest

**File**: `src/main/java/org/gerken/harmony/TooManyMovesTest.java`

#### Purpose

Detects tiles with more remaining moves than could possibly be used given board dimensions.

#### Logic

```java
// Maximum moves any tile could use is approximately:
int maxMoves = Math.max(board.getRowCount(), board.getColumnCount()) - 1;

// Check each tile
if (tile.getRemainingMoves() > maxMoves) {
    return true;  // Invalid!
}
```

#### Example

On a 4×4 board:
- Maximum useful moves ≈ 3 (traverse full row/column)
- If a tile has 10 remaining moves → **Invalid**

#### Why It Works

A tile can only move within its row or column. The maximum distance it could travel is the board dimension minus 1. More moves than this are impossible to use up.

#### Performance

⚡ **Very Fast**: O(N×M) where N×M is board size

### 2. ImpossibleColorAlignmentTest

**File**: `src/main/java/org/gerken/harmony/ImpossibleColorAlignmentTest.java`

#### Purpose

Detects when there aren't enough tiles of a required color to fill all rows that need that color.

#### Logic

```java
// Count tiles of each color on the board
Map<String, Integer> colorCounts = countTilesPerColor(board);

// Count tiles needed for each color (based on row targets)
Map<String, Integer> colorNeeded = calculateNeededPerColor(board);

// Check if we have enough
for (String color : colorNeeded.keySet()) {
    if (colorCounts.get(color) < colorNeeded.get(color)) {
        return true;  // Not enough tiles!
    }
}
```

#### Example

Board with 3 rows, 3 columns (9 tiles total):
- Row A needs RED (3 tiles)
- Row B needs BLUE (3 tiles)
- Row C needs RED (3 tiles)

Requires: 6 RED, 3 BLUE

If board has: 5 RED, 4 BLUE → **Invalid** (need 6 RED)

#### Why It Works

This is a pigeonhole principle check. If we need N tiles of a color to fill target rows, but only M < N exist on the board, it's impossible to solve.

#### Performance

⚡ **Fast**: O(N×M) - two passes over the board

### 3. InsufficientMovesTest

**File**: `src/main/java/org/gerken/harmony/InsufficientMovesTest.java`

#### Purpose

Detects tiles that are stuck in the wrong position (0 moves remaining but wrong color for their row).

#### Logic

```java
for (each tile in board) {
    String targetColor = board.getRowTargetColor(tile.row);

    if (tile.getRemainingMoves() == 0 && !tile.getColor().equals(targetColor)) {
        return true;  // Stuck in wrong place!
    }
}
```

#### Example

Row A needs RED tiles:
- Position A1 has a BLUE tile with 0 remaining moves → **Invalid**

#### Why It Works

If a tile has no moves left, it cannot be moved to a different position. If it's in the wrong row (color mismatch), it will remain wrong forever.

#### Performance

⚡ **Very Fast**: O(N×M) - single pass over board

## Adding New Tests

### Step-by-Step Guide

#### 1. Create Test Class

Create a new file implementing `InvalidityTest`:

```java
package org.gerken.harmony;

public class YourNewTest implements InvalidityTest {

    private static final YourNewTest INSTANCE = new YourNewTest();

    private YourNewTest() {
        // Private constructor for singleton
    }

    public static YourNewTest getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isInvalid(BoardState boardState) {
        // Your pruning logic here
        return false;
    }

    @Override
    public String getName() {
        return "YourNewTest";
    }
}
```

#### 2. Register in Coordinator

Edit `InvalidityTestCoordinator.java`:

```java
private InvalidityTestCoordinator() {
    List<InvalidityTest> testList = new ArrayList<>();

    testList.add(TooManyMovesTest.getInstance());
    testList.add(ImpossibleColorAlignmentTest.getInstance());
    testList.add(InsufficientMovesTest.getInstance());
    testList.add(YourNewTest.getInstance());  // ← Add here

    this.tests = Collections.unmodifiableList(testList);
}
```

#### 3. Test Thoroughly

Critical: Ensure your test never prunes valid states!

```java
@Test
public void testDoesNotPruneValidSolution() {
    BoardState validSolution = /* create a known valid solution */;
    assertFalse(YourNewTest.getInstance().isInvalid(validSolution));
}
```

### Test Ideas

Potential new invalidity tests:

1. **Deadlock Detection**: Tiles that need to swap but both have 0 moves
2. **Distance Check**: Tile needs to move N positions but has < N moves
3. **Parity Check**: Some puzzles may have parity constraints
4. **Color Cluster**: Required color is trapped in wrong row
5. **Minimum Moves Required**: Lower bound on moves needed exceeds available moves

### Critical Warning

⚠️ **False Positives Are Fatal**: If a test incorrectly marks a valid state as invalid, you'll prune paths to valid solutions. The solver will report "no solution" even when one exists.

**Always test conservatively**: When in doubt, return `false` (not invalid).

## Best Practices

### 1. Keep Tests Stateless

```java
// ✅ Good: No mutable state
public class GoodTest implements InvalidityTest {
    public boolean isInvalid(BoardState state) {
        int count = 0;  // Local variable
        // ... logic
        return false;
    }
}

// ❌ Bad: Mutable state
public class BadTest implements InvalidityTest {
    private int counter = 0;  // UNSAFE in multi-threaded environment!

    public boolean isInvalid(BoardState state) {
        counter++;  // Race condition!
        return false;
    }
}
```

### 2. Optimize for Speed

Tests run on **every generated state**. Optimize hot paths:

```java
// ✅ Fast: Early exit
for (int i = 0; i < board.getRowCount(); i++) {
    if (checkCondition(i)) {
        return true;  // Exit immediately
    }
}

// ❌ Slow: Unnecessary work
boolean invalid = false;
for (int i = 0; i < board.getRowCount(); i++) {
    if (checkCondition(i)) {
        invalid = true;  // Keeps looping!
    }
}
return invalid;
```

### 3. Order Tests by Speed

Put fastest tests first in coordinator registration:

```java
// Register in order of execution speed (fastest first)
testList.add(VeryFastTest.getInstance());      // O(1)
testList.add(FastTest.getInstance());          // O(N)
testList.add(MediumSpeedTest.getInstance());   // O(N×M)
testList.add(SlowTest.getInstance());          // O(N²×M²)
```

### 4. Document Your Logic

Explain **why** the test is valid:

```java
/**
 * Detects when the sum of available moves is less than the minimum
 * number of moves required to solve the puzzle.
 *
 * Rationale: If we need at least K swaps to reach the solution, but
 * tiles only have M < K total moves available, it's impossible.
 */
public class InsufficientTotalMovesTest implements InvalidityTest {
    // ...
}
```

## Performance Considerations

### Measurement

Track test performance:

```java
long start = System.nanoTime();
boolean result = test.isInvalid(state);
long elapsed = System.nanoTime() - start;
// Log or accumulate statistics
```

### Optimization Strategies

1. **Cache Computations**: If computing board statistics, cache results in `BoardState`
2. **Lazy Evaluation**: Check simplest conditions first
3. **Parallel Testing**: For very expensive tests, consider parallel evaluation
4. **Adaptive Testing**: Skip expensive tests for "shallow" states (few moves)

### Example: Optimization

```java
// Before: Recalculates every time
public boolean isInvalid(BoardState state) {
    Map<String, Integer> colors = countColors(state.getBoard());  // Expensive
    return checkColors(colors);
}

// After: Cache in BoardState (requires modification to BoardState class)
public boolean isInvalid(BoardState state) {
    Map<String, Integer> colors = state.getColorCounts();  // Cached
    return checkColors(colors);
}
```

### Benchmark Results

Example timings per test call (4×4 board):

| Test | Time (μs) | Complexity |
|------|-----------|------------|
| TooManyMovesTest | 0.5 | O(N×M) |
| InsufficientMovesTest | 0.8 | O(N×M) |
| ImpossibleColorAlignmentTest | 2.1 | O(N×M) |

Total overhead: ~3.4 μs per state evaluation

## Related Documentation

- [Architecture](ARCHITECTURE.md) - How pruning fits into the system
- [Data Models](DATA_MODELS.md) - BoardState and Board structure
- [Development Guide](DEVELOPMENT.md) - Testing and debugging

[← Back to README](../README.md)
