# Current State of Harmony Puzzle Solver
**Last Updated**: January 14, 2026 (Session 10b)

## Quick Status
- ✅ **Production Ready**: All code compiles and tests pass
- ✅ **Depth-First Search**: Implemented to prevent queue explosion
- ✅ **State Management**: Encapsulated in PendingStates class
- ✅ **Fully Documented**: All changes documented for future sessions
- ✅ **Thread-Safe**: All operations properly synchronized
- ✅ **BoardState Linked List**: Memory-efficient state chain structure
- ✅ **Performance Instrumented**: Average processing time displayed
- ✅ **Centralized Context**: HarmonySolver is now instance-based with getters
- ✅ **Simplified Board**: Target color ID = row index
- ✅ **Invalidity Stats**: -i flag for detailed invalidity tracking
- ✅ **Optimized Atomics**: Batch updates, pre-allocated structures
- ✅ **Queue Replication**: -repl flag for reduced contention with multiple threads
- ✅ **State Persistence**: -dur flag for timer-based checkpointing and resumption
- ✅ **Duration Time Units**: -dur supports s/m/h/d/w unit suffixes
- ✅ **Solution File Output**: Solutions with step-by-step board states
- ✅ **BOARD Format**: New simpler puzzle specification format
- ✅ **Fixed-Width Time Format**: Progress shows `[hhh:mm:ss]` elapsed time

## Current Architecture (High Level)

```
HarmonySolver (instance-based, central context)
    ├─> Config (threadCount, reportInterval, cacheThreshold, debugMode,
    │           invalidityStats, sortMode, replicationFactor, durationMinutes)
    ├─> SortMode enum (NONE, SMALLEST_FIRST, LARGEST_FIRST)
    ├─> colorNames (public static List<String> - global color mapping)
    ├─> PendingStates (2D array of ConcurrentLinkedQueues by depth × replication)
    │       ├─> queuesByMoveCount: ConcurrentLinkedQueue<BoardState>[][]
    │       ├─> activeQueues: boolean[][] (tracks which queues have been used)
    │       ├─> maxMoveCount: int (set once at init)
    │       ├─> replicationFactor: int (from -repl flag, default 3)
    │       ├─> solutionFound: volatile boolean
    │       ├─> getQueueContext(): QueueContext (factory for per-thread context)
    │       └─> addStatesGenerated(int), addStatesPruned(int)
    ├─> QueueContext (per-thread, provides random queue index)
    │       └─> getRandomQueueIndex(): int (0 to replicationFactor-1)
    ├─> List<StateProcessor> (worker threads with local caches + timing)
    │       ├─> cache: ArrayList<BoardState>(100_000)
    │       ├─> queueContext: QueueContext (obtained once at thread start)
    │       └─> trackInvalidity: boolean (only track when -i flag set)
    ├─> ProgressReporter (takes HarmonySolver, accesses all via getters)
    ├─> StateSerializer (static utility for state persistence)
    │       ├─> saveStates(puzzleFile, states)
    │       ├─> loadStates(puzzleFile, initialState)
    │       └─> State file: <puzzle>.state.txt
    └─> InvalidityTestCoordinator (ordered by effectiveness)
            ├─> BlockedSwapTest      (1st - uses targetRow = color directly)
            ├─> StuckTilesTest       (2nd)
            ├─> IsolatedTileTest     (3rd)
            ├─> StalemateTest        (4th)
            └─> WrongRowZeroMovesTest (5th)

Board (simplified)
    ├─> grid: Tile[][]
    └─> getRowTargetColor(row) returns row  # Target color = row index

BoardState (linked list structure)
    ├─> board: Board
    ├─> lastMove: Move (null for initial state)
    ├─> previousBoardState: BoardState (null for initial state)
    └─> remainingMoves: int (cached)

Tile (immutable)
    ├─> color: int
    ├─> remainingMoves: int
    ├─> copy() - returns new Tile with same values
    └─> decrementMoves() - returns new Tile with moves-1
```

## Recent Changes (Session 10b - January 14, 2026)

### 1. Enhanced Solution File with Board Visualization
**Solution files now show step-by-step board states**:
- Modified `printSolution()` in `HarmonySolver.java` (lines 376-435)
- Two sections: move sequence (compact) + step-by-step board states
- Shows initial state and board after each move for visualization

### 2. Fixed-Width Progress Time Format
**Changed elapsed time to `[hhh:mm:ss]` format**:
- Modified `formatDuration()` in `ProgressReporter.java` (lines 192-199)
- Old: `[1m 15s]` → New: `[000:01:15]`
- Aligns output better in logs, supports runs up to 999 hours

## Earlier Changes (Session 10 - January 14, 2026)

### 3. Enhanced Duration Argument (-dur)
**Support time units for flexible duration specification**:
- Modified `parseDuration()` method in `HarmonySolver.java`
- Supported units: `s` (seconds), `m` (minutes, default), `h` (hours), `d` (days), `w` (weeks)
- Examples: `30` = 30 min, `30h` = 30 hours, `5d` = 5 days, `1w` = 1 week

### 4. Solution File Output
**Write solutions to file in addition to console**:
- Modified `printSolution()` and added `getSolutionFilePath()` in `HarmonySolver.java`
- If puzzle is `name.txt`, solution writes to `name.solution.txt`
- Solution file includes header comments and numbered move sequence

### 5. New BOARD Section Format
**Simpler puzzle specification format**:
- Added BOARD section parsing in `BoardParser.java`
- Format: `BOARD` header followed by lines of `<color_name> <tile1> <moves1> ...`
- Colors listed in target row order (first color = row 0 target)
- Old COLORS/TARGETS/TILES format still fully supported

### 6. Updated PuzzleGenerator and TestBuilder
**Output puzzles in new BOARD format**:
- Both utilities now output the simpler BOARD format
- Groups tiles by color for better readability
- TestBuilder adds "End of Puzzle Specification" marker before solution

## Previous Session (Session 9 - January 13, 2026)

- **Conditional Invalidity Stats**: Only track when -i flag set
- **Active Queues Optimization**: Boolean array to skip never-used queues
- **Queue Replication**: -repl flag for reduced contention
- **QueueContext Class**: Per-thread random queue selection
- **State Persistence**: Timer-based checkpointing and resumption
- **StateSerializer**: State file save/load utilities

## File Structure

```
harmony/
├── src/main/java/org/gerken/harmony/
│   ├── HarmonySolver.java          # ⭐ UPDATED - duration units, solution file
│   ├── PuzzleGenerator.java        # ⭐ UPDATED - BOARD format output
│   ├── TestBuilder.java            # ⭐ UPDATED - BOARD format output
│   ├── TileBenchmark.java
│   ├── model/
│   │   ├── Tile.java
│   │   ├── Board.java
│   │   ├── Move.java
│   │   └── BoardState.java
│   ├── logic/
│   │   ├── PendingStates.java      # ⭐ REFACTORED - 2D queues, activeQueues, collectAllStates
│   │   ├── QueueContext.java       # ⭐ NEW - per-thread random queue selection
│   │   ├── StateSerializer.java    # ⭐ NEW - state persistence and loading
│   │   ├── StateProcessor.java     # ⭐ UPDATED - trackInvalidity, getCachedStates
│   │   ├── ProgressReporter.java   # ⭐ UPDATED - fixed-width time format [hhh:mm:ss]
│   │   └── BoardParser.java        # ⭐ UPDATED - BOARD format parsing
│   └── invalidity/
│       ├── InvalidityTest.java
│       ├── InvalidityTestCoordinator.java
│       ├── BlockedSwapTest.java
│       ├── StuckTilesTest.java
│       ├── StuckTileTest.java
│       ├── IsolatedTileTest.java
│       ├── StalemateTest.java
│       └── WrongRowZeroMovesTest.java
├── docs/
│   ├── ARCHITECTURE.md
│   ├── DATA_MODELS.md
│   ├── OPTIMIZATIONS.md
│   ├── INVALIDITY_TESTS.md
│   └── DEVELOPMENT.md
├── puzzles/
│   ├── tiny.txt
│   ├── easy.txt
│   ├── simple.txt
│   ├── medium.txt
│   ├── hard.txt
│   ├── 3x3_8moves.txt
│   ├── 4x4_9moves.txt
│   └── 3x3_12moves.txt
├── SESSION_2026-01-14.md           # ⭐ NEW - this session
├── SESSION_2026-01-13.md
├── SESSION_2026-01-12.md
├── SESSION_2026-01-11_afternoon.md
├── SESSION_2026-01-11.md
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
  -repl <N>             Replication factor for queue distribution (default: 3)
  -dur, --duration <N>  Run duration with optional unit suffix (default: 120m)
                        Units: s (seconds), m (minutes), h (hours), d (days), w (weeks)
                        Examples: 30 (30 min), 30h (30 hours), 5d (5 days)
  -d, --debug           Debug mode: disable empty queue termination
  -i, --invalidity      Show invalidity test statistics instead of queue sizes
  --smallestFirst       Process moves with smallest tile sum first
  --largestFirst        Process moves with largest tile sum first
  -h, --help            Show help message
```

## Testing Status

### Verified Working ✅
| Puzzle | Size | Moves | Processed | Pruned |
|--------|------|-------|-----------|--------|
| tiny.txt | 2x2 | 4 | 4 | 0% |
| easy.txt | 2x2 | 3 | 4 | 0% |
| simple.txt | 3x3 | 9 | 23.0K | 30.7% |
| 3x3_8moves.txt | 3x3 | 8 | 147 | 26.0% |
| 4x4_9moves.txt | 4x4 | 9 | 37 | 20.0% |
| 3x3_12moves.txt | 3x3 | 12 | 21 | 6.6% |

## Session History

### Session 10b (January 14, 2026 - Later)
- **Enhanced Solution File**: Now includes step-by-step board states after each move
- **Fixed-Width Time Format**: Progress reports use `[hhh:mm:ss]` format

### Session 10 (January 14, 2026)
- **Duration Time Units**: -dur now supports s/m/h/d/w suffixes (e.g., 30h, 5d)
- **Solution File Output**: Solutions written to `<name>.solution.txt`
- **BOARD Format**: New simpler puzzle specification format
- **PuzzleGenerator Updated**: Outputs BOARD format
- **TestBuilder Updated**: Outputs BOARD format

### Session 9 (January 13, 2026)
- **Conditional Invalidity Stats**: Only track when -i flag set
- **Active Queues**: Boolean array to skip never-used queues
- **Queue Replication**: -repl flag for reduced contention
- **QueueContext**: Per-thread random queue selection
- **2D Queue Arrays**: queuesByMoveCount[depth][replica]
- **State Persistence**: -dur flag, StateSerializer, resume from state file
- **Graceful Shutdown**: 10 second wait for in-progress states to finish

### Session 8 (January 12, 2026)
- **PendingStates Refactored**: Array-based queues, simplified atomics
- **Move Sorting**: --smallestFirst / --largestFirst flags
- **StateProcessor Optimized**: Batch counters, pre-sized cache
- **BlockedSwapTest Simplified**: Direct color→row mapping
- **Tile.copy()**: Added and benchmarked (clone() 20x faster)
- **New Puzzles**: 3x3_8moves, 4x4_9moves, 3x3_12moves
- **BoardState.isSolved() Early Exit**: Check remainingMoves before tile iteration
- **Grid Copy Benchmark**: Confirmed clone() 4-5x faster than manual copy

### Session 7 Afternoon (January 11, 2026)
- **StalemateTest**: New invalidity test
- **Board Simplified**: Target color = row index
- **Invalidity Stats**: -i flag with table display
- **Test Reordering**: BlockedSwap first

### Session 7 Morning (January 11, 2026)
- **BoardState Refactored**: Linked list structure
- **Timing Instrumentation**: Processing time tracking

### Previous Sessions
- Session 6b: ProgressReporter redesign
- Session 6: Horizontal perfect swap optimization
- Session 5: TestBuilder utility
- Session 4: HarmonySolver instance-based refactor
- Session 3: StuckTilesTest, HashMap lookups
- Session 2: PendingStates, depth-first search
- Session 1: Package reorganization

## Quick Reference

### Build and Run
```bash
mvn package                          # Build
./solve.sh puzzle.txt                # Solve
./solve.sh -t 4 puzzle.txt           # 4 threads
./solve.sh -t 4 -repl 4 puzzle.txt   # 4 threads, 4 queue replicas
./solve.sh -i -r 10 puzzle.txt       # Invalidity stats every 10s
./solve.sh --smallestFirst puzzle.txt  # Sort moves by smallest sum
```

### Key Files for Next Session
1. `CURRENT_STATE.md` - This file (start here!)
2. `SESSION_2026-01-14.md` - Latest session's details (includes 10b changes)
3. `HarmonySolver.java` - Duration with time units, solution file with board visualization
4. `ProgressReporter.java` - Fixed-width `[hhh:mm:ss]` time format
5. `BoardParser.java` - BOARD format parsing
6. `PuzzleGenerator.java` - BOARD format output
7. `TestBuilder.java` - BOARD format output

---

**Ready for**: Production use, long-running puzzles with state persistence
**Status**: ✅ Stable, documented, tested
**Last Test**: January 14, 2026 (Session 10b)
