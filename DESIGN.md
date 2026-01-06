# Harmony Puzzle Solver Design

## Puzzle Description
- Board: grid of tiles (each with a color and a number).
- Goal: align rows by color and reduce all numbers to zero.

## Solution Approach
- Recursively explore board states.
- Swap tiles (row/column), decrement numbers.
- Prune impossible states with defined tests.

## Technical Choices
- Language: Java
- Concurrency: multi-threaded processing with `ConcurrentLinkedQueue`.
- Offload states to filesystem when queue is large.

## Progress Updates
- Periodic status updates during long runs.

# Objects

- Tile:  a square game piece with a color and a number of remaining moves
- Board: a grid of rows (labeled A, B, C, etc.) and columns (labeled 1, 2, 3, etc.). The board has a unique target color for each row. Each position in the board has a tile.
- Move:  the selection of two tile in the same row or column. The two tiles exchange positions on the board and the number of remaining moves for both tiles is reduced by one. The move contains the row/column labels of the two chosen tiles (e.g. A5-C5 is the exchange of the first and third tiles in the fifth column)
- Solved Board: A board in which every tile has the same color as the target color for its current row and every tile has zero remaining moves  
- Original Board: A board for which we have to find a sequence of moves that result in a Solved Board 
- Invalid Board: A board which can not result in a Solved Board by any number of moves
- Board State: Includes a series of moves taken from the Original Board and the Board resulting from those moves
- Pending State: A Board State which can be reached from the Original State but which (1) does not have an Invalid Board and (2) has not been recursively processed, yet.

# High Level Design

- read in the original board and from that create an Original State
- create an empty list of pending states
- add the original state to the list of pending states
- if the list of pending states is not empty then process one pending board state:
  - remove a board state from the pending list
  - for every possible move (every combination of two tiles in a row or i a column)
    - create a new board state with the board that results from the move.
    - if the resulting board has an invalid board, do nothing
    - if the resulting board is a solved board the write the sequence of moves and stop the program
    - otherwise write the resulting board to the list of pending states

# Implementation Notes
- the list of pending states is implemented with a ConcurrentLinkedQueue
- the task of processing a pending board state (if one is present) should be handled by a class that implements the Runnable interface
- the number of threads running this runnable class is a configuration option

---

# Implementation Details (As Built)

## Color Representation Decision

**Decision**: Use `int` for color representation instead of `String`

**Rationale**:
- **Memory Efficiency**: int uses 4 bytes vs String overhead (16+ bytes)
- **Performance**: Primitive int comparison (`==`) is ~10x faster than `String.equals()`
- **Hash Performance**: Integer keys in HashMap are more efficient
- **Type Safety**: Compile-time checks for color operations

**Implementation**:
- `Tile.color`: `int` (color ID)
- `Board.rowTargetColors`: `int[]` (array of color IDs)
- Input files maintain human-readable color names in COLORS section
- Parser maps color names to integer IDs

**Example**:
```
COLORS
RED 0
BLUE 1
GREEN 2

TILES
A1 0 2    # Uses color ID (0=RED)
```

## Input File Format

**Final Format**:
```
ROWS <number>
COLS <number>

COLORS
<color_name> <color_id>
...

TARGETS <color_name1> <color_name2> ...

TILES
<position> <color_id> <moves>
...
```

**Key Points**:
- COLORS section serves as legend (human readability)
- TARGETS uses color names (more readable for goals)
- TILES uses color IDs (concise and efficient)
- Parser validates color IDs and names

## Complete Class Structure

### Core Data Models
- **Tile** (immutable): color (int), remainingMoves (int)
- **Board** (mostly immutable): Tile[][], int[] rowTargetColors
- **Move** (immutable): row1, col1, row2, col2, notation string
- **BoardState** (immutable): Board, List<Move> moveHistory

### Invalidity Tests (Thread-Safe Singletons)
1. **TooManyMovesTest**: Detects tiles with impossible move counts
2. **ImpossibleColorAlignmentTest**: Insufficient tiles of required colors
3. **InsufficientMovesTest**: Tiles stuck in wrong positions (0 moves, wrong color)
4. **InvalidityTestCoordinator**: Runs all tests, early exit on first failure

### Application Components
- **HarmonySolver**: Main class, orchestrates solving
- **StateProcessor**: Worker threads (Runnable), processes board states
- **ProgressReporter**: Background thread, periodic progress updates
- **BoardParser**: Parses input files with color mapping

### Utilities
- **PuzzleGenerator**: Creates solvable puzzles by working backwards from solved state
  - Starts with solved board (all tiles match rows, 0 moves)
  - Makes N random moves in reverse (incrementing move counts)
  - Guarantees solvability

## Key Design Patterns

1. **Singleton Pattern**: All invalidity tests (thread-safe, eager initialization)
2. **Immutability**: All data models immutable for thread safety
3. **Strategy Pattern**: InvalidityTest interface with multiple implementations
4. **Producer-Consumer**: ConcurrentLinkedQueue with multiple workers
5. **Command Pattern**: Move represents operations on board

## Thread Safety

- **Immutable Objects**: Tile, Move, BoardState (safe to share)
- **Board**: Mutable during construction, but swap() creates new instance
- **ConcurrentLinkedQueue**: Thread-safe queue for pending states
- **Atomic Variables**: AtomicBoolean, AtomicLong for coordination
- **Stateless Validators**: InvalidityTests have no mutable state

## Command-Line Interface

### HarmonySolver
```bash
java org.gerken.harmony.HarmonySolver [options] <puzzle-file>

Options:
  -t, --threads <N>    Worker threads (default: CPU cores)
  -r, --report <N>     Report interval in seconds (default: 30)
  -h, --help           Show help
```

### PuzzleGenerator
```bash
java org.gerken.harmony.PuzzleGenerator <rows> <cols> <colors> <moves> <output>

Arguments:
  <rows>    Number of rows (must equal number of colors)
  <cols>    Number of columns
  <colors>  Comma-separated names (e.g., RED,BLUE,GREEN)
  <moves>   Number of random moves to scramble
  <output>  Output file path
```

## Testing Strategy

- **Unit Tests**: Individual class testing (Tile, Board, Move)
- **Invalidity Tests**: Verify pruning correctness
- **Integration Tests**: End-to-end solving with known puzzles
- **Generated Puzzles**: Use PuzzleGenerator for various difficulties

## Future Enhancement Ideas

1. **State Deduplication**: Track visited boards to avoid reprocessing
2. **Heuristic Search (A*)**: Priority queue with estimated distance to solution
3. **Bidirectional Search**: Forward from start + backward from goal
4. **Parallel Pruning**: Run invalidity tests concurrently
5. **Adaptive Threading**: Dynamic thread pool adjustment
6. **Solution Optimization**: Find shortest path, not just any solution
7. **Statistics Dashboard**: Detailed metrics and visualization

## Known Limitations

1. No duplicate state detection (can process same board multiple times)
2. BFS finds *a* solution, not necessarily the shortest
3. No filesystem offloading implemented yet (planned feature)
4. Memory-bound by queue size (no spilling to disk)

## Build Information

- **Maven POM**: org.gerken:harmony-solver:1.0-SNAPSHOT
- **Java Version**: 11+ required
- **Main Classes**:
  - `org.gerken.harmony.HarmonySolver`
  - `org.gerken.harmony.PuzzleGenerator`

