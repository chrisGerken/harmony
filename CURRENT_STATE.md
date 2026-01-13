# Current State of Harmony Puzzle Solver
**Last Updated**: January 13, 2026 (Session 9)

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

## Recent Changes (Session 9 - January 13, 2026)

### 1. Conditional Invalidity Statistics
**Only track when -i flag is specified**:
- Added `trackInvalidity` boolean to `StateProcessor`
- `incrementInvalidityCounter()` only called when tracking enabled
- Reduces atomic operations when detailed stats aren't needed

### 2. Active Queues Optimization
**Skip polling empty queues using boolean tracking**:
- Added `activeQueues` boolean[][] to `PendingStates`
- In `add()`: sets `activeQueues[moveCount][queueIndex] = true`
- In `poll()`: skips queues where `activeQueues` is false
- Avoids polling queues that have never had states added

### 3. Queue Replication (-repl parameter)
**Distribute states across multiple queues to reduce contention**:
- New `-repl <N>` command-line parameter (default: 3)
- `queuesByMoveCount` changed to 2D array: `[maxMoveCount+1][replicationFactor]`
- States distributed randomly across replicas

### 4. QueueContext Class
**Per-thread context for random queue selection**:
- New class `org.gerken.harmony.logic.QueueContext`
- `getRandomQueueIndex()` returns random int using `ThreadLocalRandom`
- Each `StateProcessor` gets its own instance via `pendingStates.getQueueContext()`

### 5. Updated PendingStates Methods
**Support for 2D queue structure**:
- Constructor: `PendingStates(int maxMoveCount, int replicationFactor)`
- `add(BoardState, QueueContext)`: uses random queue index
- `poll(QueueContext)`: polls from randomly selected queue
- `isEmpty()`, `size()`: iterate over all replicated queues
- `collectAllStates()`: gathers all states for persistence

### 6. State Persistence (-dur/--duration)
**Timer-based checkpointing for long-running puzzles**:
- New `-dur, --duration <N>` parameter (default: 120 minutes)
- On timeout: signals threads to stop, waits 10 seconds for in-progress work
- Collects states from queues and caches, saves to `<puzzle>.state.txt`
- On startup: checks for state file and resumes if found
- Handles changing replication factors and thread counts between runs
- Statistics restart from zero when resuming

### 7. StateSerializer Class
**State file management**:
- `saveStates()`: Writes move histories to state file
- `loadStates()`: Replays moves to reconstruct board states
- `deleteStateFile()`: Removes state file when solution found

## File Structure

```
harmony/
├── src/main/java/org/gerken/harmony/
│   ├── HarmonySolver.java          # ⭐ UPDATED - replicationFactor, -repl flag
│   ├── PuzzleGenerator.java
│   ├── TestBuilder.java
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
│   │   ├── ProgressReporter.java
│   │   └── BoardParser.java
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
├── SESSION_2026-01-13.md           # ⭐ NEW - this session
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
  -dur, --duration <N>  Run for N minutes, then save state and exit (default: 120)
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
2. `SESSION_2026-01-13.md` - This session's details
3. `HarmonySolver.java` - Duration timer, state persistence, resumption logic
4. `StateSerializer.java` - State file save/load
5. `PendingStates.java` - 2D queue arrays, collectAllStates()
6. `StateProcessor.java` - trackInvalidity, getCachedStates()

---

**Ready for**: Production use, long-running puzzles with state persistence
**Status**: ✅ Stable, documented, tested
**Last Test**: January 13, 2026
