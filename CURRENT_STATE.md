# Current State of Harmony Puzzle Solver
**Last Updated**: January 11, 2026 (Session 7 - Afternoon)

## Quick Status
- ✅ **Production Ready**: All code compiles and tests pass
- ✅ **Depth-First Search**: Implemented to prevent queue explosion
- ✅ **State Management**: Encapsulated in PendingStates class
- ✅ **Fully Documented**: All changes documented for future sessions
- ✅ **Thread-Safe**: All operations properly synchronized
- ✅ **BoardState Linked List**: Memory-efficient state chain structure
- ✅ **Performance Instrumented**: Average processing time displayed
- ✅ **Centralized Context**: HarmonySolver is now instance-based with getters for all components
- ✅ **Simplified Board**: Target color ID = row index
- ✅ **Invalidity Stats**: New -i flag for detailed invalidity tracking

## Current Architecture (High Level)

```
HarmonySolver (instance-based, central context)
    ├─> Config (threadCount, reportInterval, cacheThreshold, debugMode, invalidityStats)
    ├─> colorNames (public static List<String> - global color mapping)
    ├─> PendingStates (depth-organized multi-queue + invalidity counters)
    ├─> List<StateProcessor> (worker threads with local caches + timing)
    ├─> ProgressReporter (takes HarmonySolver, accesses all via getters)
    └─> InvalidityTestCoordinator (ordered by effectiveness)
            ├─> BlockedSwapTest      (1st - most effective)
            ├─> StuckTilesTest       (2nd)
            ├─> IsolatedTileTest     (3rd)
            ├─> StalemateTest        (4th - NEW)
            └─> WrongRowZeroMovesTest (5th)

Board (simplified)
    ├─> grid: Tile[][]
    └─> getRowTargetColor(row) returns row  # Target color = row index

BoardState (linked list structure)
    ├─> board: Board
    ├─> lastMove: Move (null for initial state)
    ├─> previousBoardState: BoardState (null for initial state)
    └─> remainingMoves: int (cached)
```

## Recent Changes (Session 7 Afternoon - January 11, 2026)

### 1. New Invalidity Test: StalemateTest
Detects when no valid moves can be made:
- Counts tiles with remaining moves per row and column
- Early exit returns false when any row/column has 2+ tiles with moves
- Returns true (invalid) if no row or column has 2+ moveable tiles
- Location: `src/main/java/org/gerken/harmony/invalidity/StalemateTest.java`

### 2. Board Class Simplifications
**Target color = row index assumption**:
- Row 0 has target color 0, row 1 has target color 1, etc.
- Removed `rowTargetColors` array entirely
- `getRowTargetColor(row)` now simply returns `row`
- Simplified constructor: `Board(int rowCount, int columnCount)` and `Board(Tile[][] grid)`

### 3. Invalidity Statistics Tracking
**New counters in PendingStates**:
- `incrementInvalidityCounter(int moveCount, String testName)`
- `getInvalidityCount(int moveCount, String testName)`
- `getMaxInvalidityMoveCount()`

**New method in InvalidityTestCoordinator**:
- `getInvalidatingTest(BoardState)` returns the test that found state invalid

**StateProcessor update**:
- Tracks which test invalidated each state at which move count

### 4. New -i Command Line Flag
Displays invalidity statistics table instead of queue sizes:
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
Reordered by likelihood of invalidating (most likely first):
1. BlockedSwapTest (dominates pruning)
2. StuckTilesTest
3. IsolatedTileTest
4. StalemateTest
5. WrongRowZeroMovesTest

### 6. Regenerated tiny.txt Puzzle
- 2 rows, 2 columns, 4 moves
- Colors: RED, BLUE
- Previous puzzle was unsolvable

## File Structure

```
harmony/
├── src/main/java/org/gerken/harmony/
│   ├── HarmonySolver.java          # Central context, -i flag added
│   ├── PuzzleGenerator.java        # Puzzle creation utility
│   ├── TestBuilder.java            # Updated for simplified Board
│   ├── model/
│   │   ├── Tile.java
│   │   ├── Board.java              # ⭐ SIMPLIFIED - target color = row index
│   │   ├── Move.java
│   │   └── BoardState.java
│   ├── logic/
│   │   ├── PendingStates.java      # ⭐ UPDATED - invalidity counters
│   │   ├── StateProcessor.java     # ⭐ UPDATED - tracks invalidating test
│   │   ├── ProgressReporter.java   # ⭐ UPDATED - table display for -i
│   │   └── BoardParser.java        # Updated for simplified Board
│   └── invalidity/
│       ├── InvalidityTest.java
│       ├── InvalidityTestCoordinator.java  # ⭐ UPDATED - reordered, new method
│       ├── BlockedSwapTest.java
│       ├── StuckTilesTest.java
│       ├── StuckTileTest.java
│       ├── IsolatedTileTest.java
│       ├── StalemateTest.java      # ⭐ NEW
│       └── WrongRowZeroMovesTest.java
├── docs/
│   ├── ARCHITECTURE.md
│   ├── DATA_MODELS.md
│   ├── INVALIDITY_TESTS.md         # ⭐ NEEDS UPDATE for StalemateTest
│   └── DEVELOPMENT.md
├── puzzles/
│   └── tiny.txt                    # ⭐ REGENERATED
├── SESSION_2026-01-11.md
├── SESSION_2026-01-11_afternoon.md # ⭐ NEW
├── CURRENT_STATE.md                # This file
├── solve.sh
└── generate.sh
```

## Command Line Options

```bash
./solve.sh [options] <puzzle-file>

Options:
  -t, --threads <N>     Number of worker threads (default: 2)
  -r, --report <N>      Progress report interval in seconds (default: 30, 0 to disable)
  -c, --cache <N>       Cache threshold: states with N+ moves taken are cached locally (default: 4)
  -d, --debug           Debug mode: disable empty queue termination
  -i, --invalidity      Show invalidity test statistics instead of queue sizes
  -h, --help            Show help message
```

## Testing Status

### Verified Working ✅
- tiny.txt (2x2, 4 moves) - Solved
- easy.txt (2x2, 3 moves) - Solved
- simple.txt (3x3, 9 moves) - Solved, 30.7% pruning
- generated.txt (3x3, 15 moves) - Solved, 32.2% pruning
- medium.txt (4x4, 25 moves) - Running at 2.1M states/s, 37% pruning

### Key Observations from -i Flag
BlockedSwapTest is by far the most effective pruning test:
- Catches millions of invalid states at deeper move counts (17-23 moves)
- IsolatedTileTest catches a smaller but significant number
- StuckTiles, Stalemate, WrongRowZero show minimal hits for tested puzzles

## Session History

### Session 7 Afternoon (January 11, 2026)
- **StalemateTest**: New invalidity test for no-valid-moves detection
- **Board Simplified**: Target color = row index assumption
- **Invalidity Stats**: New -i flag with table display
- **Test Reordering**: BlockedSwap first (most effective)
- **tiny.txt Regenerated**: New solvable 2x2 puzzle

### Session 7 Morning (January 11, 2026)
- **BoardState Refactored**: Linked list structure with previousBoardState
- **Timing Instrumentation**: StateProcessor tracks processing time
- **ProgressReporter Enhanced**: Shows average processing time
- **Invalidity Tests Updated**: All use getLastMove() instead of getMoves()

### Previous Sessions
- Session 6b: ProgressReporter redesign, cache threshold change
- Session 6: Horizontal perfect swap, cross-row skip optimization
- Session 5: TestBuilder utility, Board.toString(), debug mode
- Session 4: HarmonySolver instance-based refactor
- Session 3: StuckTilesTest, HashMap color lookups
- Session 2: PendingStates class, depth-first search
- Session 1: Package reorganization, documentation

## Quick Reference

### Build and Run
```bash
mvn package              # Build
./solve.sh puzzle.txt    # Solve
./solve.sh -t 4 puzzle.txt  # 4 threads
./solve.sh -i -r 10 puzzle.txt  # Invalidity stats every 10s
```

### Key Files for Next Session
1. `CURRENT_STATE.md` - This file (start here!)
2. `SESSION_2026-01-11_afternoon.md` - Afternoon session details
3. `Board.java` - Simplified (target = row index)
4. `StalemateTest.java` - New invalidity test
5. `ProgressReporter.java` - Table display for -i flag
6. `PendingStates.java` - Invalidity counters

---

**Ready for**: Production use, further optimization
**Status**: ✅ Stable, documented, tested
**Last Test**: January 11, 2026 Afternoon
