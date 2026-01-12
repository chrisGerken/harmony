# Solver Optimizations

This document details the optimization strategies implemented in the Harmony Puzzle Solver to achieve efficient state-space exploration.

## Overview

The solver employs fourteen complementary optimization strategies that work together to dramatically reduce the search space and improve performance:

1. **Invalidity Tests** - Prune impossible states after generation
2. **Horizontal Perfect Swap Detection** - Force optimal endgame moves for aligned rows *(Added: 2026-01-10)*
3. **Vertical Perfect Swap Detection** - Force optimal endgame moves for column swaps
4. **Move Filtering** - Eliminate wasteful moves before generation
5. **Thread-Local Caching** - Reduce contention on shared queues *(Added: 2026-01-08)*
6. **Cached State Metrics** - Avoid recalculating remaining moves *(Added: 2026-01-08)*
7. **HashMap Color Lookups** - O(1) color-to-row mapping *(Added: 2026-01-08)*
8. **Array-Based Queue Storage** - Pre-allocated queues with direct indexing *(Added: 2026-01-12)*
9. **Batch Counter Updates** - Reduce atomic operation overhead *(Added: 2026-01-12)*
10. **Pre-Sized Cache** - Avoid ArrayList resize operations *(Added: 2026-01-12)*
11. **Simplified Solution Flag** - Volatile boolean instead of AtomicBoolean *(Added: 2026-01-12)*
12. **Direct Color-to-Row Mapping** - Eliminate search in BlockedSwapTest *(Added: 2026-01-12)*
13. **Move Sorting Options** - Configurable move ordering strategies *(Added: 2026-01-12)*
14. **BoardState.isSolved() Early Exit** - O(1) check before tile iteration *(Added: 2026-01-12)*

## 1. Invalidity Tests

Four invalidity tests identify board states that cannot lead to solutions. These tests run after a state is generated but before successor states are explored.

### StuckTilesTest *(Updated: 2026-01-08)*

**Detects:** Rows where all tiles have correct colors, all tiles have 0 or 1 remaining moves, and there's an **odd number** of tiles with 1 remaining move.

**Why Invalid:** When all tiles have correct colors, they can only swap within the row (swapping outside introduces wrong colors). Each swap reduces two tiles from 1 move to 0. An odd number of tiles with 1 move cannot all be paired off - one will always remain.

**Example:** 3 tiles with 1 move cannot all reach 0 (3 ÷ 2 = 1 remainder).

**Optimization:** Only checks rows affected by the last move.

**Improvement over StuckTileTest:** The original StuckTileTest only caught the case of exactly 1 tile with 1 move. StuckTilesTest catches any odd number (1, 3, 5, etc.), providing more comprehensive pruning.

### WrongRowZeroMovesTest

**Detects:** Tiles with 0 remaining moves whose color doesn't match their current row's target.

**Why Invalid:** A tile with 0 moves cannot move to a different row, so it's permanently in the wrong position.

**Optimization:** Only checks the two tiles involved in the last move.

### BlockedSwapTest

**Detects:** Tiles with 1 remaining move that cannot reach their target row because the tile in the target position (same column) has 0 moves.

**Why Invalid:** The tile needs to swap into its target row, but the blocking tile cannot participate in a swap.

**Optimization:** Only checks the two tiles involved in the last move.

### IsolatedTileTest *(Added: 2026-01-08)*

**Detects:** Tiles with non-zero remaining moves where ALL other tiles in both the same row AND same column have 0 moves.

**Why Invalid:** The tile has no valid swap partners. It cannot reduce its move count because swaps require both tiles to have moves remaining.

**Optimization:** Only checks the two tiles involved in the last move.

**Impact:** Contributes to 30-40% overall pruning rate alongside other tests.

## 2. Horizontal Perfect Swap Detection *(Added: 2026-01-10)*

**Location:** `StateProcessor.generateAllMoves()` - lines 142-160, 283-349

**Detects:** Rows where:
- All tiles have the target color for that row
- All tiles have 0 or 1 remaining moves
- There is an even number of tiles with 1 remaining move (at least 2)

**Action:** Returns ONLY one move from that row (any pair of tiles with 1 move), ignoring all other possible moves.

**Rationale:** When a row has all correct colors and all tiles have 0-1 moves, the only remaining work is pairing off the 1-move tiles within the row. Any pair of 1-move tiles can be swapped to make progress.

**Cross-Row Skip Optimization:**
When checking a row and finding a tile with the wrong color, the method also marks the target row for that tile's color as "skip" in a boolean array. This avoids redundant checking of rows known to be ineligible (they're missing at least one tile).

```java
if (tile.getColor() != targetColor) {
    // Mark the tile's target row as skip
    Integer tileTargetRow = colorToTargetRow.get(tile.getColor());
    if (tileTargetRow != null) {
        skipRow[tileTargetRow] = true;
    }
    return null;
}
```

**Implementation Notes:**
- Uses `checkHorizontalPerfectSwap()` helper method
- Each row checked only once (O(cols) per row)
- Runs BEFORE move generation for early return
- Tracks first two columns with 1-move tiles to avoid needing a list

## 3. Vertical Perfect Swap Detection *(Added: 2026-01-08)*

**Location:** `StateProcessor.generateAllMoves()` - lines 190-217

**Detects:** Two tiles in the same column where:
- Both have exactly 1 remaining move
- Tile1's color matches Tile2's row target
- Tile2's color matches Tile1's row target

**Action:** Returns ONLY this move, ignoring all other possible moves.

**Rationale:** This is an optimal endgame move - both tiles will end up in their correct positions with 0 moves. Forcing this move:
- Eliminates branching at critical decision points
- Guarantees progress toward solution
- Reduces search tree exploration

**Impact:** On puzzles with endgame scenarios, dramatically reduces branching factor from dozens/hundreds to 1.

## 4. Move Filtering *(Added: 2026-01-08)*

**Location:** `StateProcessor.generateAllMoves()` - lines 174-207

**Filters Out:** Moves involving tiles with exactly 1 remaining move unless the swap partner is in the tile's target row.

**Logic:**
```java
For each potential move (T1, T2):
  If T1 has 1 move remaining:
    - T2 must be in T1's target row
    - Otherwise, T1 wastes its last move
  If T2 has 1 move remaining:
    - T1 must be in T2's target row
    - Otherwise, T2 wastes its last move
```

**Rationale:** Tiles with 1 move must use it wisely. Swapping with a tile not in the target row wastes the move and likely leads to an unsolvable state.

**Impact:**
- Reduces generated states by **90-91%** on complex puzzles
- Simple 3x3: 374K → 36K states (90% reduction)
- Medium 4x4: 13.8B → 1.2B states (91% reduction)

## Performance Comparison

### Before Optimizations (baseline with 3 invalidity tests)
- 3x3 puzzle: 374,103 generated, 86% pruned
- 4x4 medium (20 min): 13.8 billion generated, 89% pruned

### After All Optimizations (4 invalidity tests + filtering)
- 3x3 puzzle: 35,636 generated, 31% pruned (90% reduction)
- 4x4 medium (8 min): 1.2 billion generated, 35% pruned (91% reduction)
- 2x2 tiny: 3 generated, 0% pruned (only solution path)
- 4x4 easy: 30 generated, 0% pruned (highly optimized)

## Why Lower Pruning Rate is Better

The pruning rate decreased from 86-89% to 30-40%, but this is actually positive:

**High Pruning Rate (Before):**
- Generating many invalid states
- Wasting CPU cycles creating and testing bad states
- Higher memory pressure

**Lower Pruning Rate (After):**
- Generating fewer, higher-quality states
- States that ARE generated are more likely valid
- Less work for invalidity tests
- More efficient overall

**Analogy:** It's better to only invite 10 qualified candidates to an interview than to invite 100 people and reject 90 of them.

## Implementation Notes

### Thread Safety
All optimization code is thread-safe:
- Move filtering operates on local state
- Invalidity tests use immutable Board objects
- No shared mutable state

### Solution Completeness
These optimizations maintain solution completeness:
- Perfect swap: Only triggers when move is provably optimal
- Move filtering: Only eliminates moves that waste last moves
- Invalidity tests: Only prune states that cannot lead to solutions

### Order of Operations
1. Check for horizontal perfect swap → return immediately if found
2. Generate all possible moves (row/column swaps with moves remaining)
3. Check for vertical perfect swap → return immediately if found
4. Filter wasteful last-move swaps
5. Return filtered move list
6. For each move: create successor, check invalidity, add to queue if valid

## 5. Thread-Local Caching *(Added: 2026-01-08)*

### Problem

In multi-threaded environments, all worker threads contend for access to the shared `ConcurrentLinkedQueue` instances. As thread count increases, this contention becomes a performance bottleneck, limiting scalability.

### Solution

Each `StateProcessor` worker maintains a private `ArrayList<BoardState>` cache for near-solution states:

```java
private final ArrayList<BoardState> cache;
private final int cacheThreshold;  // Configurable, default 4
```

**Storage Logic:**
```java
private void storeBoardState(BoardState state) {
    if (state.getRemainingMoves() < cacheThreshold) {
        cache.add(state);  // Keep locally
    } else {
        pendingStates.add(state);  // Share globally
    }
}
```

**Retrieval Logic (LIFO for depth-first):**
```java
private BoardState getNextBoardState() {
    if (!cache.isEmpty()) {
        return cache.remove(cache.size() - 1);  // Most recent state
    }
    return pendingStates.poll();
}
```

### Why This Works

- **Near-solution states** (< 4 moves remaining) benefit from locality
- **LIFO order** maintains depth-first search behavior and minimizes cache growth
- **Reduced contention** on shared queues improves scalability
- **Configurable threshold** allows tuning via `-c` flag

### Performance Impact

**Medium Puzzle (4x4, 4 threads):**
- Queue size: 298-361 (extremely stable)
- Processing rate: 3.67M states/second (sustained)
- No queue explosion even with billions of states generated

### Configuration

```bash
# Default (4 moves)
./solve.sh -t 4 puzzles/medium.txt

# Higher threshold (more caching)
./solve.sh -t 8 -c 6 puzzles/hard.txt

# Disable caching (cache threshold 0)
./solve.sh -t 2 -c 0 puzzles/simple.txt
```

## 6. Cached State Metrics *(Added: 2026-01-08)*

### Problem

The `storeBoardState()` method needs to know how many moves remain to decide whether to cache locally or add to shared queue. Previously, this required iterating through all tiles on the board for every generated state, adding O(rows × cols) overhead.

### Solution

Add a cached `remainingMoves` field to `BoardState`:

```java
public class BoardState {
    private final Board board;
    private final List<Move> moves;
    private final int remainingMoves;  // NEW: cached value
}
```

**Calculation Strategy:**
- **Original board**: Calculate once by summing all tiles' moves ÷ 2
- **Successor states**: Simply decrement by 1 (each move reduces two tiles by 1 each)

**Implementation:**
```java
// Public constructor - calculates from scratch
public BoardState(Board board, List<Move> moves) {
    this.board = board;
    this.moves = new ArrayList<>(moves);
    this.remainingMoves = calculateRemainingMoves(board);  // Only for original
}

// Private constructor - uses pre-calculated value
private BoardState(Board board, List<Move> moves, int remainingMoves) {
    this.board = board;
    this.moves = new ArrayList<>(moves);
    this.remainingMoves = remainingMoves;  // Already known
}

// Used in applyMove() - no recalculation needed
public BoardState applyMove(Move move) {
    Board newBoard = board.swap(...);
    List<Move> newMoves = new ArrayList<>(moves);
    newMoves.add(move);
    return new BoardState(newBoard, newMoves, remainingMoves - 1);  // Decrement
}
```

### Performance Impact

- **Eliminated**: O(rows × cols) calculation per generated state
- **Replaced with**: O(1) field access or simple decrement
- **Benefit scales**: More significant on larger boards (4x4, 5x5, 6x6)

On a 4x4 board generating millions of states, this optimization eliminates billions of tile iterations.

## 7. HashMap Color Lookups *(Added: 2026-01-08)*

### Problem

During move generation with last-move filtering, the solver needs to find which row has a given color as its target. The original implementation used linear search through all rows:

```java
private int findTargetRowForColor(Board board, int color) {
    for (int row = 0; row < board.getRowCount(); row++) {
        if (board.getRowTargetColor(row) == color) {
            return row;
        }
    }
    return -1;
}
```

For a board with `r` rows and `m` moves to evaluate, this created O(m × r) overhead for every board state processed.

### Solution

Build a HashMap once per board to map colors to their target rows:

```java
private Map<Integer, Integer> buildColorToRowMap(Board board) {
    Map<Integer, Integer> colorToRow = new HashMap<>();
    for (int row = 0; row < board.getRowCount(); row++) {
        int targetColor = board.getRowTargetColor(row);
        colorToRow.put(targetColor, row);
    }
    return colorToRow;
}
```

Then use O(1) lookups instead of O(r) searches:

```java
private List<Move> generateAllMoves(Board board) {
    // Build map once
    Map<Integer, Integer> colorToTargetRow = buildColorToRowMap(board);

    // ... generate moves ...

    // O(1) lookup instead of O(r) search
    Integer targetRow = colorToTargetRow.get(tile.getColor());
}
```

### Performance Impact

**Complexity Improvement:**
- **Before**: O(m × r) for all color lookups
- **After**: O(r + m) - build map once O(r), then O(1) per lookup

**Example**: For a 6x6 board (r=6) evaluating 100 moves (m=100):
- **Before**: 100 × 6 = 600 operations
- **After**: 6 + 100 = 106 operations
- **Improvement**: 5.7× faster

**Benefits Scale**:
- More significant for larger boards (4x4, 5x5, 6x6)
- Called once per `generateAllMoves()`, which processes millions of states
- On complex puzzles, eliminates tens of millions of linear searches

### Code Location

- **File**: `StateProcessor.java`
- **Method**: `buildColorToRowMap()` (new), `generateAllMoves()` (updated)

## 8. Array-Based Queue Storage *(Added: 2026-01-12)*

### Problem

The original `PendingStates` class used `ConcurrentHashMap<Integer, ConcurrentLinkedQueue<BoardState>>` to organize queues by move depth. This incurred overhead from:
- HashMap lookups for every add/poll operation
- Dynamic queue creation with `computeIfAbsent()`
- AtomicInteger updates for maxMoveCount

### Solution

Since the maximum move count is known from the initial board state, pre-allocate all queues at construction:

```java
// Before
private final ConcurrentHashMap<Integer, ConcurrentLinkedQueue<BoardState>> queuesByMoveCount;
private final AtomicInteger maxMoveCount;

// After
private final ConcurrentLinkedQueue<BoardState>[] queuesByMoveCount;
private final int maxMoveCount;
```

**Constructor:**
```java
public PendingStates(int maxMoveCount) {
    this.maxMoveCount = maxMoveCount;
    this.queuesByMoveCount = (ConcurrentLinkedQueue<BoardState>[])
        new ConcurrentLinkedQueue[maxMoveCount + 1];
    for (int i = 0; i <= maxMoveCount; i++) {
        this.queuesByMoveCount[i] = new ConcurrentLinkedQueue<>();
    }
}
```

**Benefits:**
- Direct array indexing O(1) instead of HashMap lookup
- No dynamic queue creation during execution
- No atomic updates to maxMoveCount
- Predictable memory allocation

## 9. Batch Counter Updates *(Added: 2026-01-12)*

### Problem

In `processState()`, every generated state called `incrementStatesGenerated()` and every pruned state called `incrementStatesPruned()`. Each call performed an atomic `incrementAndGet()` operation, creating contention.

### Solution

Count locally in the loop, then batch update atomics once:

```java
int generatedCount = 0;
int prunedCount = 0;

for (Move move : possibleMoves) {
    BoardState nextState = state.applyMove(move);
    generatedCount++;

    if (isInvalid(nextState)) {
        prunedCount++;
        continue;
    }
    storeBoardState(nextState);
}

// Single atomic update after loop
pendingStates.addStatesGenerated(generatedCount);
pendingStates.addStatesPruned(prunedCount);
```

**New methods in PendingStates:**
```java
public void addStatesGenerated(int count) {
    statesGenerated.addAndGet(count);
}

public void addStatesPruned(int count) {
    statesPruned.addAndGet(count);
}
```

**Benefits:**
- One atomic operation per state processed, not per state generated
- Reduces contention on shared counters
- More efficient for states with many successors

## 10. Pre-Sized Cache *(Added: 2026-01-12)*

### Problem

The thread-local cache ArrayList started empty and resized dynamically as states were added. For deep searches, this caused multiple resize operations.

### Solution

Initialize with capacity of 100,000:

```java
this.cache = new ArrayList<>(100_000);
```

**Benefits:**
- Eliminates resize operations
- Predictable memory allocation
- One-time cost at thread startup

## 11. Simplified Solution Flag *(Added: 2026-01-12)*

### Problem

The `solutionFound` flag used `AtomicBoolean` with `compareAndSet()`, but this was overkill since:
- The flag only transitions false → true (never back)
- Multiple threads setting it to true is harmless
- The exact timing of the transition doesn't affect correctness

### Solution

Use `volatile boolean` instead:

```java
// Before
private final AtomicBoolean solutionFound;
public boolean markSolutionFound(BoardState state) {
    if (solutionFound.compareAndSet(false, true)) {
        solution.set(state);
        return true;
    }
    return false;
}

// After
private volatile boolean solutionFound;
public void markSolutionFound(BoardState state) {
    solution.set(state);
    solutionFound = true;
}
```

**Benefits:**
- Simpler code
- No CAS overhead
- Volatile ensures visibility across threads

## 12. Direct Color-to-Row Mapping *(Added: 2026-01-12)*

### Problem

`BlockedSwapTest.findTargetRowForColor()` iterated through rows to find which row had a given color as its target.

### Solution

Since target color ID equals row index by convention, eliminate the search:

```java
// Before
int targetRow = findTargetRowForColor(board, tile.getColor());

// After
int targetRow = tile.getColor();  // Target row = color ID
```

**Benefits:**
- Eliminates O(rows) search per tile checked
- Direct O(1) mapping

## 13. Move Sorting Options *(Added: 2026-01-12)*

### Purpose

Allow experimentation with move ordering strategies to potentially find solutions faster.

### Implementation

**New enum:**
```java
public enum SortMode { NONE, SMALLEST_FIRST, LARGEST_FIRST }
```

**Command-line flags:**
- `--smallestFirst`: Sort moves by ascending sum of tile remaining moves
- `--largestFirst`: Sort moves by descending sum of tile remaining moves

**Comparator:**
```java
private Comparator<Move> createMoveComparator(Board board) {
    return (m1, m2) -> {
        int sum1 = board.getTile(m1.getRow1(), m1.getCol1()).getRemainingMoves()
                 + board.getTile(m1.getRow2(), m1.getCol2()).getRemainingMoves();
        int sum2 = board.getTile(m2.getRow1(), m2.getCol1()).getRemainingMoves()
                 + board.getTile(m2.getRow2(), m2.getCol2()).getRemainingMoves();
        return Integer.compare(sum1, sum2);
    };
}
```

**Usage:**
```bash
./solve.sh --smallestFirst puzzles/medium.txt
./solve.sh --largestFirst puzzles/medium.txt
```

## 14. BoardState.isSolved() Early Exit *(Added: 2026-01-12)*

### Problem

The `isSolved()` method delegated directly to `board.isSolved()`, which iterates through all tiles to verify each matches its row's target color. This O(rows × cols) operation was performed even when the board clearly couldn't be solved (remaining moves > 0).

### Solution

Add an early exit check for remaining moves before tile iteration:

```java
public boolean isSolved() {
    if (remainingMoves != 0) {
        return false;
    }
    return board.isSolved();
}
```

### Rationale

A board cannot be solved if there are remaining moves, because:
- Each tile must end with 0 remaining moves
- The `remainingMoves` field tracks total moves remaining across all tiles
- If `remainingMoves > 0`, at least one tile still needs to move

### Performance Impact

- **Before**: O(rows × cols) for every `isSolved()` call
- **After**: O(1) when moves remain, O(rows × cols) only at endgame
- Most calls return early since solution check happens at every state

### Benchmark Note

A temporary benchmark (`BoardCopyBenchmark.java`) was also created to compare grid copy strategies:
- `clone()` for each row: 34.7ms for 1M ops (3x3)
- Manual element copy: 145.2ms for 1M ops (3x3)
- **Result**: `clone()` is 4-5x faster due to native bulk memory copy
- Confirms current Board constructor implementation is optimal
- Benchmark deleted after testing

## Future Optimization Opportunities

Potential areas for further improvement:

1. **Symmetry Detection:** Recognize equivalent board states rotated/reflected
2. **Pattern Databases:** Pre-compute costs for common endgame patterns
3. **Bidirectional Search:** Search from both initial and goal states
4. **Transposition Tables:** Cache visited states to avoid recomputation
5. **Heuristic Ordering:** Prioritize moves likely to lead to solutions
6. **Parallel Pruning:** Distribute invalidity tests across threads

## Benchmarking

Test puzzles located in `puzzles/` directory:
- `tiny.txt` - 2x2, quick baseline test
- `simple.txt` - 3x3, demonstrates 90% reduction
- `easy.txt` - 2x2, optimal path generation
- `medium.txt` - 4x4, complex case showing 91% reduction
- `hard.txt` - 5x5, stress test (billions of states)

Run benchmarks with:
```bash
./solve.sh -t 4 -r 10 puzzles/simple.txt
```

Monitor:
- States generated (should be low)
- Pruning rate (30-40% is healthy)
- Queue size (should stay under 2000)
- Processing rate (1-2M states/sec)

## References

- `StateProcessor.java` - Move generation, filtering, perfect swap detection, sorting, and thread-local caching
- `PendingStates.java` - Array-based queues, batch counter methods
- `BoardState.java` - Cached remainingMoves field, isSolved() early exit
- `InvalidityTestCoordinator.java` - Test registration
- `IsolatedTileTest.java` - Isolation detection test
- `BlockedSwapTest.java` - Direct color-to-row mapping
- `HarmonySolver.java` - Command-line option parsing (-c, --smallestFirst, --largestFirst)
- Session notes: 2026-01-08 - Optimization implementation and thread-local caching
- Session notes: 2026-01-10 - Horizontal perfect swap with cross-row skip optimization
- Session notes: 2026-01-12 - Array queues, batch updates, simplified atomics, move sorting, isSolved() early exit
