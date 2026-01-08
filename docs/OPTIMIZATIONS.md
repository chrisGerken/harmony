# Solver Optimizations

This document details the optimization strategies implemented in the Harmony Puzzle Solver to achieve efficient state-space exploration.

## Overview

The solver employs three complementary optimization strategies that work together to dramatically reduce the search space:

1. **Invalidity Tests** - Prune impossible states after generation
2. **Perfect Swap Detection** - Force optimal endgame moves
3. **Move Filtering** - Eliminate wasteful moves before generation

## 1. Invalidity Tests

Four invalidity tests identify board states that cannot lead to solutions. These tests run after a state is generated but before successor states are explored.

### StuckTileTest

**Detects:** Rows where all tiles have correct colors but one tile has 1 remaining move and all others have 0.

**Why Invalid:** The tile with 1 move cannot reduce to 0 without:
- Breaking the row (swapping within row impossible - other tiles have 0 moves)
- Bringing in wrong color (swapping to another row)

**Optimization:** Only checks rows affected by the last move.

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

## 2. Perfect Swap Detection *(Added: 2026-01-08)*

**Location:** `StateProcessor.generateAllMoves()` - lines 144-172

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

## 3. Move Filtering *(Added: 2026-01-08)*

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
1. Generate all possible moves (row/column swaps with moves remaining)
2. Check for perfect swap → return immediately if found
3. Filter wasteful last-move swaps
4. Return filtered move list
5. For each move: create successor, check invalidity, add to queue if valid

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

- `StateProcessor.java` - Move generation and filtering
- `InvalidityTestCoordinator.java` - Test registration
- `IsolatedTileTest.java` - New isolation detection test
- Session notes: 2026-01-08 - Initial optimization implementation
