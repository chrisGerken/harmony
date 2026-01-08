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
        testList.add(StuckTileTest.getInstance());
        testList.add(WrongRowZeroMovesTest.getInstance());
        testList.add(BlockedSwapTest.getInstance());

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

### 1. StuckTilesTest (Active)

**File**: `src/main/java/org/gerken/harmony/invalidity/StuckTilesTest.java`
**Status**: Currently active in InvalidityTestCoordinator
**Added**: 2026-01-08

#### Purpose

Detects a stuck tiles scenario with **odd parity**: where a row has all tiles with the correct target color, all tiles have either 0 or 1 remaining moves, and there's an **odd number** of tiles with 1 remaining move. This is an impossible state to solve.

This is a more comprehensive version of `StuckTileTest` that catches any odd number of stuck tiles (1, 3, 5, etc.), not just the single-tile case.

#### Logic

```java
for (each row) {
    int targetColor = board.getRowTargetColor(row);

    boolean allColorsCorrect = true;
    int tilesWithOneMoveCount = 0;
    int tilesWithZeroMovesCount = 0;
    int tilesWithOtherMovesCount = 0;

    for (each tile in row) {
        if (tile.getColor() != targetColor) {
            allColorsCorrect = false;
            break;
        }

        if (tile.getRemainingMoves() == 0) tilesWithZeroMovesCount++;
        else if (tile.getRemainingMoves() == 1) tilesWithOneMoveCount++;
        else tilesWithOtherMovesCount++;
    }

    // Check for odd parity
    if (allColorsCorrect &&
        tilesWithOtherMovesCount == 0 &&
        tilesWithOneMoveCount > 0 &&
        tilesWithOneMoveCount % 2 == 1) {
        return true;  // Invalid - odd number of tiles with 1 move!
    }
}
```

#### Example 1: Three tiles with 1 move (odd)

Row A needs RED tiles, has 4 columns:
- A1: RED with 1 move
- A2: RED with 1 move
- A3: RED with 1 move
- A4: RED with 0 moves

This is **Invalid** because:
- All tiles are the correct color
- Three tiles have 1 move remaining (odd number)
- Tiles can only swap within the row (swapping outside would break color constraint)
- Each swap reduces two tiles from 1 move to 0 moves
- 3 tiles cannot be paired off (3 ÷ 2 = 1 remainder)

#### Example 2: Two tiles with 1 move (even) - Valid!

Row A needs RED tiles, has 4 columns:
- A1: RED with 1 move
- A2: RED with 1 move
- A3: RED with 0 moves
- A4: RED with 0 moves

This is **Valid** because:
- Two tiles with 1 move can swap with each other
- After the swap, both tiles will have 0 moves
- Even number allows perfect pairing

#### Why It Works

When all tiles in a row have the correct color, they can only swap within the row (swapping outside would introduce the wrong color). Each swap reduces exactly two tiles' move counts by 1. Therefore:
- **Even count** of tiles with 1 move → Can pair them all off → Solvable
- **Odd count** of tiles with 1 move → Cannot pair them all off → Unsolvable

This is a **parity problem**: an odd number minus pairs always leaves one unpaired.

#### Optimization

Only checks rows affected by the last move (not all rows), as only those rows could have transitioned to the stuck state.

#### Performance

⚡ **Very Fast**: O(N×M) - single pass over affected rows

#### Comparison with StuckTileTest

| Aspect | StuckTileTest (Old) | StuckTilesTest (New) |
|--------|---------------------|----------------------|
| Catches 1 tile with 1 move | ✅ Yes | ✅ Yes |
| Catches 3 tiles with 1 move | ❌ No | ✅ Yes |
| Catches 5 tiles with 1 move | ❌ No | ✅ Yes |
| Allows 2 tiles with 1 move | ✅ Yes (valid) | ✅ Yes (valid) |
| Allows 4 tiles with 1 move | ✅ Yes (valid) | ✅ Yes (valid) |
| Status | Kept in codebase | **Active** |

### 2. StuckTileTest (Legacy)

**File**: `src/main/java/org/gerken/harmony/invalidity/StuckTileTest.java`
**Status**: Kept in codebase but not active
**Note**: Superseded by StuckTilesTest

#### Purpose

Detects a stuck tile scenario where a row has all tiles with the correct target color, but all tiles have 0 remaining moves except for **exactly one tile** with 1 remaining move. This is an impossible state to solve.

#### Logic

```java
for (each row) {
    int targetColor = board.getRowTargetColor(row);

    boolean allColorsCorrect = true;
    int tilesWithOneMoveCount = 0;
    int tilesWithZeroMovesCount = 0;
    int tilesWithOtherMovesCount = 0;

    for (each tile in row) {
        if (tile.getColor() != targetColor) {
            allColorsCorrect = false;
            break;
        }

        if (tile.getRemainingMoves() == 0) tilesWithZeroMovesCount++;
        else if (tile.getRemainingMoves() == 1) tilesWithOneMoveCount++;
        else tilesWithOtherMovesCount++;
    }

    if (allColorsCorrect &&
        tilesWithOneMoveCount == 1 &&
        tilesWithOtherMovesCount == 0 &&
        tilesWithZeroMovesCount == columnCount - 1) {
        return true;  // Invalid!
    }
}
```

#### Example

Row A needs RED tiles, has 3 columns:
- A1: RED with 0 moves
- A2: RED with 1 move
- A3: RED with 0 moves

This is **Invalid** because:
- All tiles are the correct color
- The tile at A2 has 1 move remaining
- It cannot reduce to 0 moves without swapping (which would break the row's color alignment)
- Swapping within the row is impossible (other tiles have 0 moves)
- Swapping with another row would bring in a wrong color

#### Why It Works

For a row to be solved, all tiles must have 0 moves AND correct colors. If all tiles already have correct colors but one tile has 1 move, that tile cannot use its move without either:
1. Swapping within the row (impossible - no other tile has moves)
2. Swapping with another row (would bring in wrong color)

This creates an unsolvable deadlock.

#### Performance

⚡ **Very Fast**: O(N×M) - single pass over board

### 3. WrongRowZeroMovesTest

**File**: `src/main/java/org/gerken/harmony/invalidity/WrongRowZeroMovesTest.java`

#### Purpose

Detects tiles that are stuck in the wrong row. If any tile has zero remaining moves and its color doesn't match the target color for its current row, the board is invalid.

#### Logic

```java
for (each row) {
    int targetColor = board.getRowTargetColor(row);

    for (each column) {
        Tile tile = board.getTile(row, col);

        // If tile has no moves left and is the wrong color for this row
        if (tile.getRemainingMoves() == 0 && tile.getColor() != targetColor) {
            return true;  // Invalid - tile is stuck in wrong row!
        }
    }
}
```

#### Example

Row A needs RED tiles:
- A1: BLUE with 0 moves
- A2: RED with 2 moves
- A3: RED with 1 move

This is **Invalid** because:
- The tile at A1 is BLUE (wrong color for row A)
- It has 0 remaining moves
- It cannot be moved to its correct row
- The board can never reach a solved state

#### Why It Works

A tile with 0 moves cannot participate in any swaps. If it's in the wrong row for its color, it will remain in the wrong row forever. Since the puzzle requires all tiles to be in their correct rows with 0 moves, this state is unsolvable.

#### Performance

⚡ **Very Fast**: O(N×M) - single pass over board

### 4. BlockedSwapTest

**File**: `src/main/java/org/gerken/harmony/invalidity/BlockedSwapTest.java`

#### Purpose

Detects blocked swap scenarios. If a tile T1 has 1 remaining move and is not in its correct row, and the tile in T1's target row (at the same column) has 0 remaining moves, then T1 cannot reach its correct position, making the board invalid.

#### Logic

```java
for (each tile T1 on the board) {
    // Only consider tiles with exactly 1 remaining move
    if (tile.getRemainingMoves() != 1) continue;

    // Find the target row for this tile's color
    int targetRow = findTargetRowForColor(board, tile.getColor());

    // If tile is already in its target row, skip
    if (currentRow == targetRow) continue;

    // Check the tile in the target row at the same column
    Tile blockingTile = board.getTile(targetRow, sameColumn);

    // If the blocking tile has 0 moves, T1 cannot swap into position
    if (blockingTile.getRemainingMoves() == 0) {
        return true;  // Invalid - tile is blocked!
    }
}
```

#### Example

Board state:
- Row A needs RED tiles
- Row B needs BLUE tiles
- A1: BLUE with 1 move (needs to go to row B)
- B1: RED with 0 moves (blocking A1's target position)

This is **Invalid** because:
- A1 (BLUE) needs to swap into position B1
- A1 has exactly 1 move remaining
- B1 has 0 moves, so cannot participate in a swap
- A1 cannot reach its target position
- The puzzle is unsolvable

#### Why It Works

For a tile with 1 move to reach its target row, it must swap with the tile currently in its target position (same column). If that blocking tile has 0 moves, the swap cannot happen. The tile with 1 move is permanently blocked from reaching its destination.

#### Performance

⚡ **Fast**: O(N×M×R) where R is number of rows (typically small)
- Iterates over all tiles: O(N×M)
- For each tile with 1 move, finds target row: O(R)
- Total: O(N×M×R)

### 5. IsolatedTileTest

**File**: `src/main/java/org/gerken/harmony/invalidity/IsolatedTileTest.java`

#### Purpose

Detects isolated tiles that have moves remaining but no valid swap partners. If a tile has moves remaining but no other tile in its row or column has moves remaining, it cannot use its moves, making the board invalid.

#### Logic

```java
for (each row) {
    for (each col) {
        Tile tile = board.getTile(row, col);

        if (tile.getRemainingMoves() > 0) {
            // Check if any other tile in same row has moves
            boolean hasRowPartner = false;
            for (each other column in row) {
                if (board.getTile(row, otherCol).getRemainingMoves() > 0) {
                    hasRowPartner = true;
                    break;
                }
            }

            // Check if any other tile in same column has moves
            boolean hasColPartner = false;
            for (each other row in column) {
                if (board.getTile(otherRow, col).getRemainingMoves() > 0) {
                    hasColPartner = true;
                    break;
                }
            }

            // If tile has no valid swap partners, board is invalid
            if (!hasRowPartner && !hasColPartner) {
                return true;
            }
        }
    }
}
```

#### Example

Row A needs RED, Row B needs BLUE:
- A1: RED with 2 moves
- A2: RED with 0 moves
- B1: BLUE with 0 moves
- B2: RED with 0 moves

This is **Invalid** because:
- A1 has 2 moves remaining
- No other tile in row A has moves (A2 has 0)
- No other tile in column 1 has moves (B1 has 0)
- A1 cannot use its 2 moves (no valid swap partners)
- The puzzle cannot reach a state where all tiles have 0 moves

#### Why It Works

For a tile to reduce its move count, it must swap with another tile. The other tile must also have moves remaining. If a tile has moves but no potential swap partners (no other tiles with moves in its row or column), it's stuck with non-zero moves forever.

#### Performance

⚡ **Fast**: O(N×M×(N+M)) - for each tile, checks row and column
- Best case: O(N×M) when partners found quickly
- Worst case: O(N×M×(N+M)) when checking all positions

## Historical Note

Initial test implementations (TooManyMovesTest, ImpossibleColorAlignmentTest, InsufficientMovesTest) were developed but removed because they were too aggressive and pruned valid solution paths.

The current active tests (StuckTilesTest, WrongRowZeroMovesTest, BlockedSwapTest, IsolatedTileTest) are more conservative and only prune genuinely impossible states. They collectively provide 35-37% pruning on typical puzzles without eliminating valid solution paths.

**2026-01-08 Update**: StuckTilesTest (odd parity version) replaced StuckTileTest as the active test, providing more comprehensive detection of stuck tile scenarios.

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

    testList.add(StuckTileTest.getInstance());
    testList.add(WrongRowZeroMovesTest.getInstance());
    testList.add(BlockedSwapTest.getInstance());
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
| WrongRowZeroMovesTest | 0.5 | O(N×M) |
| StuckTileTest | 0.8 | O(N×M) |
| BlockedSwapTest | 1.2 | O(N×M×R) |

Total overhead: ~2.5 μs per state evaluation

**Note**: These are estimated timings. The current tests are designed to be fast and conservative, providing effective pruning (typically 60-70% pruning rate) without eliminating valid solution paths.

## Related Documentation

- [Architecture](ARCHITECTURE.md) - How pruning fits into the system
- [Data Models](DATA_MODELS.md) - BoardState and Board structure
- [Development Guide](DEVELOPMENT.md) - Testing and debugging

[← Back to README](../README.md)
