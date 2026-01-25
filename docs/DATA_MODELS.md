# Data Models

[← Back to README](../README.md)

## Table of Contents

- [Overview](#overview)
- [Tile](#tile)
- [Board](#board)
- [Move](#move)
- [BoardState](#boardstate)
- [Design Principles](#design-principles)
- [Class Relationships](#class-relationships)

## Overview

The Harmony Puzzle Solver uses four core data models to represent the puzzle state and operations. All models are **immutable** to ensure thread-safety in the multi-threaded environment.

## Tile

**File**: `src/main/java/org/gerken/harmony/Tile.java`

### Purpose

Represents a single game piece with a color and remaining move count.

### Properties

```java
public class Tile {
    private final int color;              // Tile color ID (0, 1, 2, etc.)
    private final int remainingMoves;     // Number of moves this tile can still make
}
```

**Color Representation**: Colors are stored as integers for efficiency:
- **Memory**: 4 bytes per tile vs 16+ bytes for String
- **Comparison**: Primitive `==` vs `.equals()` (~10x faster)
- **Hashing**: Direct integer hashing vs String hashing

### Key Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `getColor()` | `int` | Gets the tile's color ID |
| `getRemainingMoves()` | `int` | Gets remaining moves |
| `decrementMoves()` | `Tile` | Returns new tile with moves - 1 |
| `toString()` | `String` | Format: "ID:N" (e.g., "0:3") |

### Immutability

Tiles are immutable. To modify a tile, create a new instance:

```java
Tile original = new Tile(0, 5);  // Color ID 0 with 5 moves
Tile moved = original.decrementMoves();  // Returns new Tile(0, 4)
```

### Thread Safety

✅ **Thread-safe**: Immutable, can be shared across threads without synchronization.

## Board

**File**: `src/main/java/org/gerken/harmony/Board.java`

### Purpose

Represents the puzzle board - a 2D grid of tiles with target colors for each row.

### Structure

```java
public class Board {
    private final Tile[][] grid;           // 2D array of tiles
    private Integer score;                 // Lazy-calculated heuristic score (null until computed)
    // Target color for each row = row index (row 0 → color 0, row 1 → color 1, etc.)
}
```

**Note**: Colors are represented as `int` for efficiency. See Tile documentation for rationale.

### Board Score (Added 2026-01-23)

The board has a lazy-calculated heuristic score that measures "distance from solution":
- **Lower score = closer to solution** (solved board has score 0)
- Calculated on first call to `getScore()`, then cached

**Score Formula** (sum of):
1. Number of tiles NOT in their target row (target row = color ID)
2. For each column: (tiles in column) - (unique colors in column)
3. For each tile with > 2 remaining moves: (remaining moves - 2)

```java
Board board = /* ... */;
int score = board.getScore();  // Calculates and caches if null
System.out.println(board);     // Includes "Score: N" on last line
```

### Coordinate System

- **Rows**: Labeled A, B, C, D, ... (0-indexed internally)
- **Columns**: Labeled 1, 2, 3, 4, ... (1-indexed for display, 0-indexed internally)
- **Example**: Position "C2" = row index 2, column index 1

### Key Methods

#### Access

| Method | Returns | Description |
|--------|---------|-------------|
| `getTile(row, col)` | `Tile` | Gets tile at position |
| `setTile(row, col, tile)` | `void` | Sets tile at position |
| `getRowCount()` | `int` | Number of rows |
| `getColumnCount()` | `int` | Number of columns |
| `getRowTargetColor(row)` | `int` | Target color ID for row |

#### Operations

| Method | Returns | Description |
|--------|---------|-------------|
| `swap(r1, c1, r2, c2)` | `Board` | Returns new board with tiles swapped and moves decremented |
| `isSolved()` | `boolean` | Checks if all tiles match row colors and have 0 moves |
| `getScore()` | `int` | Returns heuristic score (calculates and caches if needed) |

### Win Condition

A board is solved when **both** conditions are met:
1. Every tile's color matches its row's target color
2. Every tile has exactly 0 remaining moves

```java
boolean solved = board.isSolved();
```

### Immutability Note

While `Board` has `setTile()` for construction, the `swap()` method creates a new `Board`:

```java
Board original = /* ... */;
Board afterSwap = original.swap(0, 0, 0, 1);  // Original unchanged
```

### Thread Safety

⚠️ **Mutable during construction**, but typically used immutably. The `swap()` method creates new boards for thread-safe state transitions.

## Move

**File**: `src/main/java/org/gerken/harmony/Move.java`

### Purpose

Represents a swap operation between two tiles in the same row or column.

### Properties

```java
public class Move {
    private final int row1, col1;    // First tile position (0-indexed)
    private final int row2, col2;    // Second tile position (0-indexed)
    private final String notation;   // Algebraic notation (e.g., "A1-A3")
}
```

### Algebraic Notation

Moves are represented in human-readable format:
- **Format**: `{Row}{Col}-{Row}{Col}`
- **Examples**:
  - `A1-A3`: Swap tiles in row A, columns 1 and 3
  - `B2-D2`: Swap tiles in column 2, rows B and D

### Key Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `getRow1()`, `getCol1()` | `int` | First tile coordinates |
| `getRow2()`, `getCol2()` | `int` | Second tile coordinates |
| `getNotation()` | `String` | Algebraic notation |
| `isValid()` | `boolean` | True if tiles are in same row or column |
| `toString()` | `String` | Returns notation |

### Validation

Only moves in the same row or column are valid:

```java
Move horizontal = new Move(0, 0, 0, 2);  // A1-A3 ✅
Move vertical = new Move(0, 1, 2, 1);    // A2-C2 ✅
Move diagonal = new Move(0, 0, 1, 1);    // A1-B2 ❌ Invalid!

horizontal.isValid();  // true
diagonal.isValid();    // false
```

### Thread Safety

✅ **Thread-safe**: Immutable value object.

## BoardState

**File**: `src/main/java/org/gerken/harmony/BoardState.java`

### Purpose

Represents a node in the search space - a board configuration plus a link to the previous state.

### Properties (Updated 2026-01-25)

```java
public class BoardState {
    private final Board board;                    // Current board configuration
    private final Move lastMove;                  // Last move taken (null for initial state)
    private final int remainingMoves;             // Cached count (sum of tile moves / 2)
    private final int bestScore;                  // Minimum score seen along path from initial
    private BoardState previousBoardState;        // Link to previous state (null for initial)
    private boolean first;                        // True if on primary search path
}
```

**Architecture Note** (Updated 2026-01-11):
BoardState now uses a **linked list structure** instead of copying move lists. Each state stores only the last move taken and a reference to the previous state. This eliminates ArrayList copying on every state transition, significantly reducing memory allocation overhead.

**Optimization Note** (Added 2026-01-08):
The `remainingMoves` field is cached to avoid recalculating on every state. For the original board state, it's computed by summing all tiles' remaining moves and dividing by 2. For successor states, it's simply decremented by 1 (since each move decrements two tiles by 1 each, net effect is -1 remaining move).

**First Flag** (Added 2026-01-24):
The `first` boolean tracks the "primary search path" - the path following the first/only valid move at each step. BoardParser sets `first=true` on the initial state. When `applyMove(Move, int possibleMoveCount)` is called, the new state inherits `first=true` only if the parent has `first=true` AND there is exactly one possible move. This enables caching strategies that keep the primary path together in one thread's local cache.

**Best Score** (Added 2026-01-25):
The `bestScore` integer tracks the minimum (best) board score seen along the path from the initial state to this state. For initial states, `bestScore = board.getScore()`. For successor states created via `applyMove()`, `bestScore = Math.min(newBoard.getScore(), parent.bestScore)`. This enables pruning states that have drifted too far from their best score via the `-sb` (steps back) command line option.

### Key Methods

#### Access

| Method | Returns | Description |
|--------|---------|-------------|
| `getBoard()` | `Board` | Current board configuration |
| `getLastMove()` | `Move` | Last move taken (null for initial state) |
| `getPreviousBoardState()` | `BoardState` | Previous state in chain (null for initial) |
| `getMoveCount()` | `int` | Number of moves taken (traverses chain) |
| `getMoveHistory()` | `List<Move>` | Complete move sequence (builds by traversal) |
| `getRemainingMoves()` | `int` | Cached count of remaining moves |
| `getBestScore()` | `int` | Minimum score seen along path from initial state |
| `isSolved()` | `boolean` | Delegates to `board.isSolved()` |
| `isFirst()` | `boolean` | True if on primary search path |

#### Operations

| Method | Returns | Description |
|--------|---------|-------------|
| `applyMove(move)` | `BoardState` | Returns new state with move applied; bestScore = min(newScore, parent.bestScore) |
| `setPreviousBoardState(state)` | `void` | Sets previous state reference |
| `setFirst(boolean)` | `void` | Sets the first flag value |

### State Transitions

Each state transition creates a new `BoardState` linked to its predecessor:

```java
BoardState initial = new BoardState(board);
// initial.getLastMove() == null
// initial.getPreviousBoardState() == null

BoardState state1 = initial.applyMove(move1);
// state1.getLastMove() == move1
// state1.getPreviousBoardState() == initial

BoardState state2 = state1.applyMove(move2);
// state2.getLastMove() == move2
// state2.getPreviousBoardState() == state1

// Getting complete history
List<Move> history = state2.getMoveHistory(); // [move1, move2]
```

**Performance**: The `applyMove()` method uses a private constructor that accepts a pre-calculated `remainingMoves` value and sets the `previousBoardState` reference. No list copying occurs during state transitions.

### Search Space

`BoardState` objects form a linked chain structure:

```
Initial State (lastMove=null, prev=null, first=true)
         │
         ▼
    State A (lastMove=Move1, prev=Initial, first=true if only move)
         │
         ▼
    State B (lastMove=Move2, prev=StateA, first=true if only move AND A.first)
         │
         ▼
    State C (lastMove=Move3, prev=StateB, first=false if multiple moves existed)
```

The `first` flag traces the "primary search path" - the path that would be taken if only the first valid move was chosen at each step. This path is kept together in one thread's local cache for efficient depth-first processing.

### Invalidity Test Pattern

Invalidity tests should use `getLastMove()` for efficient checking:

```java
Move lastMove = boardState.getLastMove();
if (lastMove == null) {
    // Initial state - check all tiles/rows
    return checkAllTiles(board);
}
// Only check tiles affected by lastMove
int row1 = lastMove.getRow1();
// ...
```

### Thread Safety

✅ **Thread-safe**: Core properties are immutable. The `previousBoardState` reference and `first` flag are set once during `applyMove()` before the state is shared.

## Design Principles

### 1. Immutability

All core data structures are immutable:
- **Benefit**: Thread-safe without synchronization
- **Trade-off**: More object allocations (mitigated by short-lived objects)

```java
// Never mutate - always create new instances
Tile newTile = oldTile.decrementMoves();
Board newBoard = oldBoard.swap(r1, c1, r2, c2);
BoardState newState = oldState.applyMove(move);
```

### 2. Value Semantics

Objects are compared by value, not identity:
- Implement `equals()` and `hashCode()`
- Enable use in hash-based collections
- Support future state deduplication

```java
Tile t1 = new Tile("RED", 3);
Tile t2 = new Tile("RED", 3);
t1.equals(t2);  // true - same color and moves
```

### 3. Defensive Copying

Collections are copied to prevent external modification:

```java
// BoardState constructor
this.moves = new ArrayList<>(moves);  // Copy the list

// Getter returns unmodifiable view
public List<Move> getMoves() {
    return Collections.unmodifiableList(moves);
}
```

### 4. Separation of Concerns

- **Tile**: Individual game piece
- **Board**: Grid structure and win condition
- **Move**: Operation representation
- **BoardState**: Search space node with history

Each class has a single, well-defined responsibility.

## Class Relationships

### UML Diagram

```
┌───────────────────────────┐
│       BoardState          │
│───────────────────────────│
│ - board: Board            │
│ - lastMove: Move          │◄──────┐
│ - previousBoardState      │───────┘ (self-reference)
│ - remainingMoves: int     │
└───────────┬───────────────┘
            │ contains
            ▼
┌─────────────────┐         ┌──────────────┐
│      Board      │ contains│     Tile     │
│─────────────────│◆────────│──────────────│
│ - grid: Tile[][]│         │ - color      │
│ - rowTargets[]  │         │ - moves      │
└─────────────────┘         └──────────────┘
         │
         │ creates
         ▼
┌─────────────────┐
│      Move       │
│─────────────────│
│ - row1, col1    │
│ - row2, col2    │
└─────────────────┘
```

### Dependencies

```
BoardState ──uses──> Board
BoardState ──uses──> Move
Board ──contains──> Tile
```

### Object Lifecycle

1. **Creation**: Parse input file → create initial `BoardState`
2. **Exploration**: Generate moves → create new `BoardState` objects
3. **Validation**: Pass `BoardState` to `InvalidityTestCoordinator`
4. **Completion**: Find solved `BoardState` → output move sequence

## Usage Examples

### Creating a Tile

```java
// Colors are represented as integers (0, 1, 2, etc.)
Tile redTile = new Tile(0, 5);  // Color ID 0 with 5 moves
System.out.println(redTile);    // "0:5"
```

### Building a Board

```java
// Target color for each row equals the row index (row 0 → color 0, row 1 → color 1, etc.)
Board board = new Board(3, 3);  // 3x3 board

board.setTile(0, 0, new Tile(0, 2));  // RED tile with 2 moves (row 0 needs color 0)
board.setTile(0, 1, new Tile(1, 3));  // BLUE tile with 3 moves
// ... set remaining tiles

// Target colors are implicit:
// Row 0 (A) needs color 0 (RED)
// Row 1 (B) needs color 1 (BLUE)
// Row 2 (C) needs color 2 (GREEN)
```

### Color Mapping (Input File)

Two file formats are supported:

**BOARD Format (Recommended)**:
```
ROWS 3
COLS 3

BOARD
RED A1 3 B2 2 C3 1
BLUE A2 2 B1 3 C2 1
GREEN A3 1 B3 2 C1 3
```

Each line after BOARD: `<color_name> <tile1> <moves1> <tile2> <moves2> ...`
Colors are listed in target row order (first color = row 0 target).

**Traditional Format**:
```
COLORS
RED 0
BLUE 1
GREEN 2

TARGETS RED BLUE GREEN

TILES
A1 0 2    # RED tile with 2 moves (uses ID, not name)
A2 1 3    # BLUE tile with 3 moves
A3 2 1    # GREEN tile with 1 move
```

### Creating a Move

```java
Move swap = new Move(0, 0, 0, 2);  // Swap A1 and A3
System.out.println(swap.getNotation());  // "A1-A3"
```

### Applying a Move

```java
BoardState initial = new BoardState(board);
Move move = new Move(0, 0, 0, 1);
BoardState next = initial.applyMove(move);

System.out.println(next.getMoveCount());  // 1
```

### Checking Solution

```java
if (state.isSolved()) {
    List<Move> solution = state.getMoveHistory();
    for (Move move : solution) {
        System.out.println(move.getNotation());
    }
}
```

## Related Documentation

- [Architecture](ARCHITECTURE.md) - How these models fit into the system
- [Invalidity Tests](INVALIDITY_TESTS.md) - How tests use these models
- [Development Guide](DEVELOPMENT.md) - Extending the data models

[← Back to README](../README.md)
