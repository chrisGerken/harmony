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
    private final int[] rowTargetColors;   // Target color IDs for each row
}
```

**Note**: Colors are represented as `int` for efficiency. See Tile documentation for rationale.

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

Represents a node in the search space - a board configuration plus the sequence of moves taken to reach it.

### Properties

```java
public class BoardState {
    private final Board board;           // Current board configuration
    private final List<Move> moves;      // Sequence of moves from original state
}
```

### Key Methods

#### Access

| Method | Returns | Description |
|--------|---------|-------------|
| `getBoard()` | `Board` | Current board configuration |
| `getMoves()` | `List<Move>` | Unmodifiable list of moves taken |
| `getMoveCount()` | `int` | Number of moves taken |
| `isSolved()` | `boolean` | Delegates to `board.isSolved()` |

#### Operations

| Method | Returns | Description |
|--------|---------|-------------|
| `applyMove(move)` | `BoardState` | Returns new state with move applied |

### State Transitions

Each state transition creates a new `BoardState`:

```java
BoardState current = /* ... */;
Move move = new Move(0, 0, 0, 1);

BoardState next = current.applyMove(move);
// current is unchanged
// next has updated board and move history
```

### Search Space

`BoardState` objects form a tree structure:

```
         Original State
         (empty move list)
        /       |        \
    State A  State B  State C
    [Move1]  [Move2]  [Move3]
    /  |  \
  ...  ...  ...
```

### Thread Safety

✅ **Thread-safe**: Immutable, safe for concurrent processing by worker threads.

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
┌─────────────────┐
│   BoardState    │
│─────────────────│
│ - board: Board  │
│ - moves: List   │
└────────┬────────┘
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
// Target colors as integer IDs
int[] targets = {0, 1, 2};  // RED=0, BLUE=1, GREEN=2
Board board = new Board(3, 3, targets);

board.setTile(0, 0, new Tile(0, 2));  // RED tile with 2 moves
board.setTile(0, 1, new Tile(1, 3));  // BLUE tile with 3 moves
// ... set remaining tiles
```

### Color Mapping (Input File)

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
    List<Move> solution = state.getMoves();
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
