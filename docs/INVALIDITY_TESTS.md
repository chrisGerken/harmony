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

### 1. FutureStuckTilesTest (Active)

**File**: `src/main/java/org/gerken/harmony/invalidity/FutureStuckTilesTest.java`
**Status**: Currently active in InvalidityTestCoordinator
**Added**: 2026-01-20 (Session 13)
**Replaces**: StuckTilesTest

#### Purpose

Detects colors that will inevitably become stuck due to parity, even before all tiles reach their target row. This is a more general test than StuckTilesTest, catching invalid states earlier in the search.

#### Conditions for Invalid State

A color is **stuck** (board invalid) if ALL of the following are true:
1. There is exactly one tile of that color in each column
2. Tiles of that color on the target row have < 3 remaining moves
3. Tiles of that color NOT on the target row have exactly 1 remaining move
4. `(sum of remaining moves - count of tiles not on target row)` is odd

#### Logic

```java
private boolean isColorStuck(Board board, int color, int rows, int cols) {
    int targetRow = color;  // Target row equals color by convention

    int[] tilesPerColumn = new int[cols];
    int sumRemainingMoves = 0;
    int tilesNotOnTargetRow = 0;

    for (int row = 0; row < rows; row++) {
        for (int col = 0; col < cols; col++) {
            Tile tile = board.getTile(row, col);

            if (tile.getColor() == color) {
                tilesPerColumn[col]++;
                int moves = tile.getRemainingMoves();
                sumRemainingMoves += moves;

                if (row == targetRow) {
                    // Condition 2: Tiles on target row must have < 3 remaining moves
                    if (moves >= 3) return false;
                } else {
                    // Condition 3: Tiles NOT on target row must have exactly 1 move
                    if (moves != 1) return false;
                    tilesNotOnTargetRow++;
                }
            }
        }
    }

    // Condition 1: Exactly one tile of this color in each column
    for (int col = 0; col < cols; col++) {
        if (tilesPerColumn[col] != 1) return false;
    }

    // Condition 4: Adjusted parity must be odd
    int effectiveMoves = sumRemainingMoves - tilesNotOnTargetRow;
    return effectiveMoves % 2 == 1;
}
```

#### Example: Invalid State

Consider RED (color 0, target row A) with tiles:
- A1: RED with 2 moves (on target row)
- B2: RED with 1 move (NOT on target row - must move to row A)
- C3: RED with 1 move (NOT on target row - must move to row A)

Conditions check:
1. ✅ One RED tile in each column (1, 2, 3)
2. ✅ A1 has < 3 moves (2)
3. ✅ B2 and C3 each have exactly 1 move
4. Sum = 2 + 1 + 1 = 4. Tiles not on target = 2. Effective = 4 - 2 = 2 (even) → Valid

But if A1 had 1 move instead:
- Sum = 1 + 1 + 1 = 3. Effective = 3 - 2 = 1 (odd) → **Invalid**

#### Why It Works

Each tile not on the target row must use exactly one move to reach the target row. After that, the remaining moves must reduce to zero through paired swaps within the row. If the effective remaining moves (after accounting for the required row-change moves) has odd parity, it cannot be reduced to zero through paired swaps.

#### Performance

⚡ **Fast**: O(R×C) where R is rows and C is columns
- Single pass over board per color
- Early exit on any condition failure
- Checks all colors (R iterations)

---

### 2. StuckTilesTest (Legacy)

**File**: `src/main/java/org/gerken/harmony/invalidity/StuckTilesTest.java`
**Status**: Legacy - replaced by FutureStuckTilesTest in Session 13
**Added**: 2026-01-08
**Note**: Kept in codebase but not active in InvalidityTestCoordinator
**Simplified**: 2026-01-11 - Cleaner parity-based detection

#### Purpose

Detects stuck tiles scenarios with **odd parity** that cannot be solved. A row is stuck when all tiles have the correct color but the remaining moves can't be zeroed out through valid swaps.

#### Conditions for Invalid State

A row is **stuck** (invalid) if ALL of the following are true:
1. All tiles in the row have the row's target color
2. Each tile has less than 3 remaining moves (0, 1, or 2)
3. The sum of remaining moves across all tiles in the row is **odd**

#### Logic (Simplified 2026-01-11)

```java
private boolean isRowStuck(Board board, int row) {
    int targetColor = board.getRowTargetColor(row);
    int sumRemainingMoves = 0;

    for (int col = 0; col < board.getColumnCount(); col++) {
        Tile tile = board.getTile(row, col);

        // Condition 1: All tiles must have the target color
        if (tile.getColor() != targetColor) {
            return false;
        }

        // Condition 2: Each tile must have < 3 moves remaining
        int moves = tile.getRemainingMoves();
        if (moves >= 3) {
            return false;
        }

        sumRemainingMoves += moves;
    }

    // Condition 3: Sum of remaining moves must be odd
    return sumRemainingMoves % 2 == 1;
}
```

#### Example 1: Odd parity - Invalid

Row A needs RED tiles, has 4 columns:
- A1: RED with 1 move
- A2: RED with 1 move
- A3: RED with 1 move
- A4: RED with 0 moves

This is **Invalid** because:
- All tiles have the correct color (RED)
- All have < 3 moves (0, 1, 1, 1)
- Sum = 3 (odd) → cannot be reduced to 0 through paired swaps

#### Example 2: Even parity - Valid

Row A needs RED tiles, has 4 columns:
- A1: RED with 2 moves
- A2: RED with 0 moves
- A3: RED with 0 moves
- A4: RED with 0 moves

This is **Valid** because:
- All tiles have correct color
- All have < 3 moves
- Sum = 2 (even) → can be reduced to 0

#### Why Odd Parity is Invalid

Each swap within the row decreases two tiles' move counts by 1 each. The net effect on the sum is always -2 (even). Starting with an odd sum, you can never reach 0 (even) through any sequence of swaps.

#### Optimization

Only checks rows affected by the last move (not all rows), as only those rows could have transitioned to the stuck state.

Uses `getLastMove()` for efficient detection:
```java
Move lastMove = boardState.getLastMove();
if (lastMove == null) {
    return checkAllRows(board);  // Initial state
}
// Only check row(s) containing tiles from the last move
```

#### Performance

⚡ **Very Fast**: O(M) per row - single pass over row tiles

#### Evolution

| Version | Detection | Status |
|---------|-----------|--------|
| StuckTileTest | Only exactly 1 tile with 1 move, all in row | Legacy |
| StuckTilesTest v1 | Any odd count with 1 move, all in row | Replaced |
| StuckTilesTest v2 | Odd parity with 0-2 moves, handles one-out-of-row | Replaced |
| StuckTilesTest v3 | Simple odd parity check, all tiles < 3 moves | **Active** |

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
**Enhanced**: 2026-01-19 (Session 12) - Now checks both T1 and T2 scenarios

#### Purpose

Detects blocked swap scenarios. A board is invalid if:
- **T1 scenario**: A tile T1 has 1 remaining move, is not in its correct row, and the tile T2 in T1's target row (same column) has 0 remaining moves
- **T2 scenario**: A tile T2 has 0 remaining moves and is blocking another tile T1 (with 1 move) that needs to reach T2's row

#### Logic

The test checks each moved tile both as a potential T1 (blocked tile) and as a potential T2 (blocking tile):

```java
// Check both tiles involved in the last move
// Each tile could be:
// 1. A blocked tile (T1): has 1 move, not in target row, blocked by 0-move tile
// 2. A blocking tile (T2): has 0 moves, blocking another tile with 1 move

if (isTileBlocked(board, row1, col1) || isTileBlocking(board, row1, col1)) {
    return true;
}
```

**isTileBlocked()** - Checks if a tile with 1 move is blocked:
```java
// Only consider tiles with exactly 1 remaining move
if (tile.getRemainingMoves() != 1) return false;

// Target row equals the tile's color (by convention)
int targetRow = tile.getColor();

// If tile is already in its target row, it's not blocked
if (row == targetRow) return false;

// Check the tile in the target row at the same column
Tile blockingTile = board.getTile(targetRow, col);

// If the blocking tile has 0 moves, this tile cannot swap into position
return blockingTile.getRemainingMoves() == 0;
```

**isTileBlocking()** - Checks if a tile with 0 moves is blocking another tile:
```java
// Only consider tiles with exactly 0 remaining moves (potential blockers)
if (tile.getRemainingMoves() != 0) return false;

// Check all other tiles in the same column
for (int otherRow = 0; otherRow < board.getRowCount(); otherRow++) {
    if (otherRow == row) continue;

    Tile otherTile = board.getTile(otherRow, col);

    // Check if this other tile is blocked by the tile at (row, col):
    // - Has exactly 1 remaining move
    // - Needs to reach row 'row' (its color == row, since target row = color)
    if (otherTile.getRemainingMoves() == 1 && otherTile.getColor() == row) {
        return true;
    }
}
return false;
```

#### Example 1: T1 Scenario (tile is blocked)

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

#### Example 2: T2 Scenario (tile is blocking)

Board state after a move places B1:
- Row A needs RED tiles
- Row B needs BLUE tiles
- A1: BLUE with 1 move (needs to go to row B)
- B1: RED with 0 moves (just moved here)

If the last move placed B1 at its current position with 0 moves, the **isTileBlocking()** check on B1 detects that A1 (1 move, needs row B) is blocked.

#### Why It Works

For a tile with 1 move to reach its target row, it must swap with the tile currently in its target position (same column). If that blocking tile has 0 moves, the swap cannot happen. The tile with 1 move is permanently blocked from reaching its destination.

The enhancement (Session 12) ensures we detect this condition regardless of whether the blocked tile (T1) or the blocking tile (T2) was involved in the last move.

#### Performance

⚡ **Fast**: O(R) per moved tile where R is number of rows
- Only checks tiles involved in the last move (optimization)
- isTileBlocked: O(1) - single lookup
- isTileBlocking: O(R) - scans column for blocked tiles
- Total per state: O(R) (typically small)

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

### 6. StalemateTest

**File**: `src/main/java/org/gerken/harmony/invalidity/StalemateTest.java`
**Added**: 2026-01-11 (Session 7 Afternoon)

#### Purpose

Detects stalemate conditions where no valid moves can be made. A board is in stalemate if no row or column has at least two tiles with remaining moves. Since a swap requires two tiles in the same row or column, if no such pair exists, the puzzle cannot progress.

#### Logic

```java
@Override
public boolean isInvalid(BoardState boardState) {
    Board board = boardState.getBoard();
    int rows = board.getRowCount();
    int cols = board.getColumnCount();

    // Track count of tiles with moves remaining per row and column
    int[] rowCounts = new int[rows];
    int[] colCounts = new int[cols];

    // Iterate over all tiles
    for (int row = 0; row < rows; row++) {
        for (int col = 0; col < cols; col++) {
            Tile tile = board.getTile(row, col);

            if (tile.getRemainingMoves() >= 1) {
                rowCounts[row]++;
                colCounts[col]++;

                // Early exit: if any row or column has 2+ tiles with moves, not a stalemate
                if (rowCounts[row] >= 2 || colCounts[col] >= 2) {
                    return false;
                }
            }
        }
    }

    // No row or column has 2+ tiles with moves remaining - stalemate
    return true;
}
```

#### Example

3x3 board state:
- A1: RED with 1 move
- A2: RED with 0 moves
- A3: RED with 0 moves
- B1: BLUE with 0 moves
- B2: BLUE with 1 move
- B3: BLUE with 0 moves
- C1: GREEN with 0 moves
- C2: GREEN with 0 moves
- C3: GREEN with 1 move

This is **Invalid** because:
- Row A: only A1 has moves (count = 1)
- Row B: only B2 has moves (count = 1)
- Row C: only C3 has moves (count = 1)
- Column 1: only A1 has moves (count = 1)
- Column 2: only B2 has moves (count = 1)
- Column 3: only C3 has moves (count = 1)
- No row or column has 2+ tiles with moves, so no swap is possible

#### Why It Works

A swap requires two tiles to participate. Both tiles must be in the same row or column, and both must have at least 1 remaining move. If no row and no column has two or more tiles with moves, no swaps can occur, and any tiles with remaining moves are stuck.

#### Performance

⚡ **Very Fast**: O(N×M) with early exit
- Single pass over board
- Returns immediately when any row/column has 2+ moveable tiles
- Most valid boards will exit early

## Test Execution Order

Tests are ordered by likelihood of finding invalid states (most effective first):

1. **BlockedSwapTest** - Most effective, catches millions of states at deeper move counts
2. **FutureStuckTilesTest** - Catches future stuck parity situations across all colors
3. **IsolatedTileTest** - Catches tiles with no swap partners
4. **StalemateTest** - Catches global no-moves situations
5. **WrongRowZeroMovesTest** - Catches tiles stuck in wrong row

## Historical Note

Initial test implementations (TooManyMovesTest, ImpossibleColorAlignmentTest, InsufficientMovesTest) were developed but removed because they were too aggressive and pruned valid solution paths.

The current active tests (BlockedSwapTest, StuckTilesTest, IsolatedTileTest, StalemateTest, WrongRowZeroMovesTest) are conservative and only prune genuinely impossible states. They collectively provide 30-40% pruning on typical puzzles without eliminating valid solution paths.

**2026-01-08 Update**: StuckTilesTest (odd parity version) replaced StuckTileTest as the active test, providing more comprehensive detection of stuck tile scenarios.

**2026-01-10 Update**: StuckTilesTest enhanced to handle the case where exactly one tile with the target color is outside its target row.

**2026-01-11 Morning Update**: StuckTilesTest simplified to cleaner parity-based detection. All invalidity tests updated to use `getLastMove()` instead of `getMoves()` following the BoardState refactoring to linked list structure.

**2026-01-11 Afternoon Update**:
- Added StalemateTest for detecting no-valid-moves conditions
- Reordered tests by effectiveness (BlockedSwap first)
- Added `-i` flag for invalidity statistics tracking
- Board simplified: target color = row index

**2026-01-19 Update (Session 12)**:
- Enhanced BlockedSwapTest to check moved tiles both as T1 (blocked) and T2 (blocking)
- Added `isTileBlocking()` method to detect when a 0-move tile blocks another tile in the same column
- Previously only checked if moved tiles were blocked; now also checks if they are blocking others

**2026-01-20 Update (Session 13)**:
- Added FutureStuckTilesTest, replacing StuckTilesTest as the active parity-based pruning test
- FutureStuckTilesTest detects stuck colors earlier by checking tiles not yet on target row
- Conditions: one tile per column, target row tiles < 3 moves, off-target tiles = 1 move, odd adjusted parity
- StuckTilesTest kept in codebase as legacy but no longer active in InvalidityTestCoordinator
- Test results show 80%+ pruning rate on complex puzzles

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
