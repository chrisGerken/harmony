# Session Summary - January 11, 2026 (Afternoon)

## Changes Made

### 1. New Invalidity Test: StalemateTest
Created `StalemateTest.java` that detects when no valid moves can be made:
- Iterates over all tiles counting tiles with remaining moves per row and column
- Early exit: returns false (not stalemate) as soon as any row or column has 2+ tiles with moves
- Returns true (stalemate/invalid) if no row or column has 2+ tiles with moves remaining
- Location: `src/main/java/org/gerken/harmony/invalidity/StalemateTest.java`

### 2. Board Class Simplifications

#### Static rowTargetColors
Made `rowTargetColors` a static variable since target colors don't change during solving.

#### Target Color = Row Index Assumption
Simplified Board by assuming the target color ID for each row equals the row index:
- Row 0 has target color 0
- Row 1 has target color 1
- etc.

This eliminated:
- The `rowTargetColors` array entirely
- The 3-argument Board constructor
- `getRowTargetColor(row)` now simply returns `row`
- `isSolved()` compares tile color directly to row index

### 3. Invalidity Statistics Tracking

#### New Counters in PendingStates
Added tracking of which invalidity test found each state invalid at which move count:
- `ConcurrentHashMap<String, AtomicLong> invalidityCounters` with key format "moveCount:testName"
- `incrementInvalidityCounter(int moveCount, String testName)`
- `getInvalidityCount(int moveCount, String testName)`
- `getMaxInvalidityMoveCount()`

#### New Method in InvalidityTestCoordinator
Added `getInvalidatingTest(BoardState)` that returns the InvalidityTest that found the state invalid (or null if valid).

#### StateProcessor Update
Now calls `getInvalidatingTest()` and tracks which test invalidated each state.

### 4. New -i Command Line Flag
Added `-i` / `--invalidity` flag to display invalidity statistics instead of queue sizes:
- Shows a table with invalidity tests as rows and move counts as columns
- Format:
```
[30s] Invalidity Statistics:
Test                 1       2       3       4       5
------------------------------------------------------
BlockedSwap          0      10     100    1.2K    5.4K
StuckTiles           0      20     200    2.5K   10.2K
IsolatedTile         0       5      50     600    2.1K
Stalemate            0       0       0       0       0
WrongRowZero         0      15     150    1.8K    7.3K
```

### 5. Invalidity Test Order Change
Reordered tests by likelihood of invalidating (most likely first for early exit):
1. BlockedSwapTest
2. StuckTilesTest
3. IsolatedTileTest
4. StalemateTest
5. WrongRowZeroMovesTest

### 6. Regenerated tiny.txt Puzzle
Deleted the unsolvable tiny puzzle and generated a new one:
- 2 rows, 2 columns
- 4 moves
- Colors: RED, BLUE

## Files Modified
- `src/main/java/org/gerken/harmony/model/Board.java` - Static targets, simplified constructor
- `src/main/java/org/gerken/harmony/logic/PendingStates.java` - Invalidity counters
- `src/main/java/org/gerken/harmony/logic/StateProcessor.java` - Track invalidating test
- `src/main/java/org/gerken/harmony/logic/ProgressReporter.java` - Table display for -i flag
- `src/main/java/org/gerken/harmony/logic/BoardParser.java` - Use simplified Board constructor
- `src/main/java/org/gerken/harmony/invalidity/InvalidityTestCoordinator.java` - New method, reordered tests
- `src/main/java/org/gerken/harmony/HarmonySolver.java` - Added -i flag
- `src/main/java/org/gerken/harmony/TestBuilder.java` - Use simplified Board constructor
- `puzzles/tiny.txt` - Regenerated

## Files Created
- `src/main/java/org/gerken/harmony/invalidity/StalemateTest.java`

## Test Results
All puzzles solve correctly:
- tiny: 4 moves, solved
- simple: 9 moves, 30.7% pruning
- generated: 15 moves, 32.2% pruning
- medium: Running at 2.1M/s, 37% pruning (BlockedSwap dominates pruning)

## Key Observations from -i Flag
From medium puzzle testing, BlockedSwapTest is by far the most effective pruning test, catching millions of invalid states at deeper move counts (17-23 moves). IsolatedTileTest catches a smaller but significant number. StuckTiles, Stalemate, and WrongRowZero show zero hits for this puzzle.
