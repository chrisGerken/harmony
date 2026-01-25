# Current State of Harmony Puzzle Solver
**Last Updated**: January 25, 2026 (Session 16)

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
- ✅ **Clean Board Display**: Zero moves hidden (shows `RED` not `RED 0`)
- ✅ **Case-Insensitive Parsing**: `dkbrown` → `DKBROWN`, `a1` → `A1`
- ✅ **Position Validation**: Duplicate tile positions detected
- ✅ **MOVES Section**: Optional section to transform board state
- ✅ **Board Scoring**: Lazy-calculated heuristic score for board states
- ✅ **ScoreWatcher Utility**: Analyzes scoring heuristic effectiveness
- ✅ **Score-Based Queuing**: PendingStates indexes by board score (lower = priority)
- ✅ **First Flag**: BoardState tracks primary search path for caching strategy
- ✅ **Best Score Tracking**: BoardState.bestScore tracks minimum score along path
- ✅ **Steps Back Pruning**: -sb flag prunes states drifting from best score

## Current Architecture (High Level)

```
HarmonySolver (instance-based, central context)
    ├─> Config (threadCount, reportInterval, cacheThreshold, stepsBack, debugMode,
    │           invalidityStats, sortMode, replicationFactor, durationMinutes)
    ├─> SortMode enum (NONE, SMALLEST_FIRST, LARGEST_FIRST)
    ├─> colorNames (public static List<String> - global color mapping)
    ├─> PendingStates (2D array of ConcurrentLinkedQueues by SCORE × replication)
    │       ├─> queuesByScore: ConcurrentLinkedQueue<BoardState>[][]
    │       ├─> activeQueues: boolean[][] (tracks which queues have been used)
    │       ├─> maxScore: int (initialScore + 6)
    │       ├─> replicationFactor: int (from -repl flag, default 3)
    │       ├─> solutionFound: volatile boolean
    │       ├─> poll(): Returns state from LOWEST score queue first
    │       ├─> getQueueContext(): QueueContext (factory for per-thread context)
    │       └─> addStatesGenerated(int), addStatesPruned(int)
    ├─> QueueContext (per-thread, provides random queue index)
    │       └─> getRandomQueueIndex(): int (0 to replicationFactor-1)
    ├─> List<StateProcessor> (worker threads with local caches + timing)
    │       ├─> cache: ArrayList<BoardState>(100_000)
    │       ├─> queueContext: QueueContext (obtained once at thread start)
    │       ├─> stepsBack: int (from -sb flag, default 1)
    │       ├─> trackInvalidity: boolean (only track when -i flag set)
    │       └─> storeBoardStates(): caches all when parent.isFirst(), prunes by stepsBack
    ├─> ProgressReporter (takes HarmonySolver, accesses all via getters)
    │       └─> Displays queues from max score down to 0
    ├─> StateSerializer (static utility for state persistence)
    │       ├─> saveStates(puzzleFile, states) - format: "bestScore:moves..."
    │       ├─> loadStates(puzzleFile, initialState) - parses bestScore prefix
    │       └─> State file: <puzzle>.state.txt
    └─> InvalidityTestCoordinator (ordered by effectiveness)
            ├─> BlockedSwapTest         (1st - uses targetRow = color directly)
            ├─> FutureStuckTilesTest    (2nd - detects future stuck parity)
            ├─> IsolatedTileTest        (3rd)
            ├─> StalemateTest           (4th)
            └─> WrongRowZeroMovesTest   (5th)

Board (simplified)
    ├─> grid: Tile[][]
    ├─> score: Integer (null until calculated, lazy initialization)
    ├─> getScore(): int (calculates and caches score if null)
    ├─> getRowTargetColor(row) returns row  # Target color = row index
    └─> toString() includes "Score: N" on its own line

BoardState (linked list structure)
    ├─> board: Board
    ├─> lastMove: Move (null for initial state)
    ├─> previousBoardState: BoardState (null for initial state)
    ├─> remainingMoves: int (cached)
    ├─> bestScore: int (min score along path from initial state)
    ├─> first: boolean (true if on primary search path)
    ├─> applyMove(Move): calculates newBestScore = min(newBoard.score, this.bestScore)
    ├─> getBestScore(): returns bestScore
    └─> isFirst(), setFirst(): accessors for first flag

Tile (immutable)
    ├─> color: int
    ├─> remainingMoves: int
    ├─> copy() - returns new Tile with same values
    └─> decrementMoves() - returns new Tile with moves-1
```

## Recent Changes (Session 16 - January 25, 2026)

### 1. BoardState bestScore Attribute
**Added `bestScore` field to track minimum score along search path**:
- New `private final int bestScore` field
- For initial state: `bestScore = board.getScore()`
- For successor states: `bestScore = Math.min(newBoard.getScore(), parent.bestScore)`
- Added `getBestScore()` getter method
- Updated `toString()` to include "Best score: N"

### 2. Steps Back Command Line Option (-sb)
**Added `-sb <N>` for score-based pruning control**:
- Default value: 1
- States with `boardScore > bestScore + stepsBack` are pruned
- Lower values = more aggressive pruning, faster but may miss solutions
- Higher values = less pruning, slower but more thorough search

### 3. StateProcessor stepsBack Pruning
**Modified storeBoardStates() to prune states drifting from best score**:
- New `stepsBack` field passed from HarmonySolver
- In normal logic section: discard states where `boardScore > bestScore + stepsBack`
- Discarded states counted via `pendingStates.addStatesPruned()`

### 4. StateSerializer bestScore Persistence
**Updated state file format to include bestScore**:
- New format: `bestScore:move1 move2 move3...`
- Example: `38:A1-A2 B1-B3`
- Backward compatible with old format (no bestScore prefix)

## Earlier Changes (Session 15 - January 24, 2026)

### 1. Score-Based Queue Indexing
**Changed PendingStates from moves-remaining to board-score indexing**:
- Constructor now takes `initialScore` instead of `maxMoveCount`
- `maxScore = initialScore + 6` (scores above max stored in max queue)
- `add()` uses `state.getBoard().getScore()` for queue index
- `poll()` returns from LOWEST score queue first (lower = closer to solution)
- `getQueueRangeInfo()` still returns all non-empty queues

### 2. Score-Based Cache Threshold (-c flag)
**Changed -c meaning from moves-taken to board-score**:
- Old: "cache states with N+ moves taken"
- New: "cache states with board score >= N"
- High-score states (far from solution) cached locally
- Low-score states (close to solution) go to shared queue for priority

### 3. ProgressReporter Queue Display Order
**Reversed display order to show max score down to 0**:
- Was: lowest score to highest (left to right)
- Now: highest score to lowest (left to right)
- Lower scores (closer to solution) appear on the right

### 4. BoardState 'first' Flag
**New boolean flag tracking primary search path**:
- Added `boolean first` field with `isFirst()` / `setFirst()` accessors
- BoardParser sets `first=true` on initial state returned to HarmonySolver
- `applyMove(Move, int possibleMoveCount)` propagates `first=true` only when:
  - Parent state has `first=true` AND
  - There is exactly one possible move from parent
- Otherwise new state has `first=false`

### 5. storeBoardStates() First Flag Logic
**Cache all states when parent is on primary path**:
- If `parent.isFirst()` is true, all child states cached locally
- Child states get `first = (states.size() == 1)`
- Otherwise, normal score-based caching logic applies
- This keeps the primary search path together in one thread's cache

### 6. Resume State Sorting
**Sort restored states by remaining moves when resuming**:
- Added sort in `HarmonySolver.solve()` before adding resumed states
- Sorts by `getRemainingMoves()` ascending (least first)
- States closest to solution are added to queues first

### 7. Display Board Score on Startup
**HarmonySolver now shows initial board score**:
- Added "Board score: N" line after "Board size: NxN"
- Helps understand puzzle complexity before solving

## Earlier Changes (Session 14 - January 23, 2026)

### 1. Board Scoring System
**Added lazy-calculated heuristic score to Board class**:
- Added `Integer score` attribute with default value null
- Added `getScore()` method that calculates and caches score on first access
- Score formula (sum of):
  - Number of tiles NOT in their target row (target row = color ID)
  - For each column: (tiles in column) - (unique colors in column)
  - For each tile with > 2 remaining moves: (remaining moves - 2)
- Updated both `toString()` methods to include "Score: N" on its own line
- Lower score = closer to solution (solved board has score 0)

### 2. ScoreWatcher Utility
**New class to analyze scoring heuristic effectiveness**:
- Created `ScoreWatcher.java` in main package
- Generates random puzzles and reports score progression through solution
- Shows for each move: score, valid moves count, min/max possible scores, rank, percentile
- Only marks score increases with ↑ (score going up = getting worse)
- Displays statistics: average percentile, optimal move rate, score increase rate
- Usage: `java ScoreWatcher <rows> <cols> <numMoves>`

### 3. TestBuilder.generatePuzzleWithSolution()
**New static method for programmatic puzzle generation**:
- Added `generatePuzzleWithSolution(int rows, int cols, int numMoves)` method
- Returns `BoardState[]` where element 0 is initial scrambled state
- Subsequent elements show state after each solution move
- Overloaded version accepts `Random` for reproducible puzzles

### 4. StateProcessor Batch Storage
**Refactored processState() to collect valid states before storing**:
- Changed from immediate `storeBoardState(state)` calls to batch collection
- Renamed `storeBoardState(BoardState)` to `storeBoardStates(List<BoardState>)`
- Valid states collected in ArrayList, then stored in batch after all moves processed
- Enables future enhancements like sorting by score before storage

## Earlier Changes (Session 13 - January 20, 2026)

### 1. New FutureStuckTilesTest
**Replaced StuckTilesTest with more general FutureStuckTilesTest**:
- Created new `FutureStuckTilesTest.java` in `invalidity/` package
- Detects colors that will inevitably become stuck due to parity, even before all tiles reach their target row
- For each color, checks if ALL conditions are true:
  1. Exactly one tile of that color in each column
  2. Tiles on target row have < 3 remaining moves
  3. Tiles NOT on target row have exactly 1 remaining move
  4. `(sum of remaining moves - tiles not on target row)` is odd
- Updated `InvalidityTestCoordinator.java` to use FutureStuckTilesTest instead of StuckTilesTest
- Updated `ProgressReporter.java` with new test name for invalidity stats display
- Test results show 80%+ pruning rate on complex puzzles

## Earlier Changes (Session 12 - January 19, 2026)

### 2. Enhanced BlockedSwapTest
**Now checks moved tiles both as T1 (blocked) and T2 (blocking)**:
- Modified `BlockedSwapTest.java` (lines 42-62, 91-128)
- Previously only checked if moved tiles were blocked (T1 with 1 move)
- Now also checks if moved tiles are blocking others (T2 with 0 moves)
- Added new `isTileBlocking()` method to detect when a 0-move tile blocks another tile in the same column

**Scenario now caught**: If a move places a tile T2 with 0 remaining moves in a position where it blocks another tile T1 (which has 1 move and needs to reach T2's row), the board is now correctly detected as invalid.

## File Structure

```
harmony/
├── src/main/java/org/gerken/harmony/
│   ├── HarmonySolver.java          # ⭐ UPDATED - added -sb option, stepsBack config
│   ├── PuzzleGenerator.java        # BOARD format output
│   ├── TestBuilder.java            # generatePuzzleWithSolution() method
│   ├── ScoreWatcher.java           # scoring heuristic analysis utility
│   ├── TileBenchmark.java
│   ├── model/
│   │   ├── Tile.java
│   │   ├── Board.java              # score attribute, getScore(), toString() shows score
│   │   ├── Move.java
│   │   └── BoardState.java         # ⭐ UPDATED - bestScore field, getBestScore()
│   ├── logic/
│   │   ├── PendingStates.java      # ⭐ UPDATED - score-based indexing, poll from lowest score
│   │   ├── QueueContext.java       # per-thread random queue selection
│   │   ├── StateSerializer.java    # ⭐ UPDATED - bestScore persistence in state file
│   │   ├── StateProcessor.java     # ⭐ UPDATED - stepsBack pruning in storeBoardStates
│   │   ├── ProgressReporter.java   # ⭐ UPDATED - reversed queue display order
│   │   └── BoardParser.java        # ⭐ UPDATED - sets first=true on initial state
│   └── invalidity/
│       ├── InvalidityTest.java
│       ├── InvalidityTestCoordinator.java
│       ├── BlockedSwapTest.java
│       ├── FutureStuckTilesTest.java
│       ├── StuckTilesTest.java             # Legacy - not active
│       ├── StuckTileTest.java              # Legacy - not active
│       ├── IsolatedTileTest.java
│       ├── StalemateTest.java
│       └── WrongRowZeroMovesTest.java
├── docs/
│   ├── ARCHITECTURE.md
│   ├── DATA_MODELS.md              # ⭐ NEEDS UPDATE - BoardState first flag
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
├── SESSION_2026-01-25.md           # ⭐ NEW - this session
├── SESSION_2026-01-24.md
├── SESSION_2026-01-23.md
├── SESSION_2026-01-19.md
├── SESSION_2026-01-15.md
├── SESSION_2026-01-14.md
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
  -c, --cache <N>       Cache threshold: states with board score >= N are cached locally (default: 4)
  -sb <N>               Steps back: max score increase from best score before pruning (default: 1)
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

### Verified Working ✅ (Session 16)
| Puzzle | Size | Score | Moves | -sb | Processed | Pruned |
|--------|------|-------|-------|-----|-----------|--------|
| tiny.txt | 2x2 | 6 | 4 | 1 | 4 | 0% |
| easy.txt | 2x2 | 4 | 3 | 2 | 5 | 0% |
| 3x3_8moves.txt | 3x3 | 9 | 8 | 1 | 30 | 51.5% |
| 3x3_12moves.txt | 3x3 | 19 | 12 | 2 | 21 | 30.4% |
| 4x4_9moves.txt | 4x4 | 7 | 9 | 1 | 58 | 42.3% |
| simple.txt | 3x3 | 6 | 9 | 2 | 613 | 42.5% |
| gen5-5.txt | 5x5 | 10 | 6 | 2 | 8 | 30.8% |

## Session History

### Session 16 (January 25, 2026)
- **Best Score Tracking**: BoardState.bestScore tracks minimum score along search path
- **Steps Back Pruning**: -sb flag controls max score increase from best before pruning
- **StateProcessor Pruning**: storeBoardStates() discards states where score > bestScore + stepsBack
- **State File Format**: Now includes bestScore prefix (e.g., `38:A1-A2`)

### Session 15 (January 24, 2026)
- **Score-Based Queuing**: PendingStates indexes by board score, polls lowest first
- **Cache Threshold**: -c now refers to board score (>= N cached locally)
- **Queue Display**: ProgressReporter shows max score down to 0
- **First Flag**: BoardState.first tracks primary search path
- **storeBoardStates()**: Caches all states when parent.isFirst()
- **Resume Sorting**: Restored states sorted by remaining moves (least first)
- **Board Score Display**: Shown on startup after board size

### Session 14 (January 23, 2026)
- **Board Scoring**: Added lazy-calculated heuristic score to Board class
- Score = (tiles not in target row) + (duplicate colors per column) + (excess moves per tile)
- **ScoreWatcher**: New utility to analyze scoring heuristic effectiveness
- **TestBuilder.generatePuzzleWithSolution()**: Static method returning BoardState array
- **StateProcessor Refactored**: processState() collects valid states, storeBoardStates(List)

### Session 13 (January 20, 2026)
- **FutureStuckTilesTest**: New invalidity test replacing StuckTilesTest
- Detects colors that will inevitably become stuck due to parity, even before all tiles reach target row
- Checks: (1) one tile per column, (2) target row tiles < 3 moves, (3) off-target tiles = 1 move, (4) odd adjusted parity
- 80%+ pruning rate on complex puzzles

### Session 12 (January 19, 2026)
- **Enhanced BlockedSwapTest**: Now checks moved tiles both as T1 (blocked) and T2 (blocking)
- Added `isTileBlocking()` method to detect when a 0-move tile blocks another tile in the same column

### Session 11 (January 15, 2026)
- **Clean Board Display**: Zero moves hidden in toString() (shows `RED` not `RED 0`)
- **Case-Insensitive Parsing**: `dkbrown` → `DKBROWN`, `a1` → `A1`
- **Position Validation**: Duplicate tile positions detected with error
- **MOVES Section**: Optional section to transform board state before solving

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

### Previous Sessions
- Session 8: PendingStates refactored, move sorting, StateProcessor optimized
- Session 7: StalemateTest, Board simplified, invalidity stats, BoardState linked list
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
./solve.sh -sb 2 puzzle.txt          # Allow score to drift 2 from best
./solve.sh -sb 0 puzzle.txt          # Strict: never allow score increase
./solve.sh -i -r 10 puzzle.txt       # Invalidity stats every 10s
./solve.sh --smallestFirst puzzle.txt  # Sort moves by smallest sum
```

### Key Files for Next Session
1. `CURRENT_STATE.md` - This file (start here!)
2. `BoardState.java` - bestScore field, getBestScore(), tracks min score along path
3. `StateProcessor.java` - stepsBack pruning in storeBoardStates()
4. `HarmonySolver.java` - -sb option, stepsBack config passed to StateProcessor
5. `StateSerializer.java` - bestScore persistence in state file format

---

**Ready for**: Production use, score-based search with stepsBack pruning
**Status**: ✅ Stable, documented, tested
**Last Test**: January 25, 2026 (Session 16)
