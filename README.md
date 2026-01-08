# Harmony Puzzle Solver

A multi-threaded Java application that solves color-matching tile puzzles using depth-first search with intelligent pruning.

## Overview

Harmony is a puzzle where:
- A grid board contains colored tiles, each with a number representing remaining moves
- Each row has a target color (represented internally as integers for efficiency)
- Players swap tiles in the same row or column (decrements both tiles' move counts)
- Goal: All tiles match their row's target color AND all have 0 moves remaining

This solver uses parallel state-space exploration to find solutions efficiently.

## Quick Start

### Compile the Project

```bash
mvn compile
# or
mvn package
```

### Generate a Puzzle

```bash
# Using convenience script (recommended)
./generate.sh 3 3 RED,BLUE,GREEN 10 puzzle.txt

# OR run directly with Java
java -cp target/classes org.gerken.harmony.PuzzleGenerator 3 3 RED,BLUE,GREEN 10 puzzle.txt

# Recommended difficulty levels:
# Easy 2x2:       java -cp target/classes org.gerken.harmony.PuzzleGenerator 2 2 RED,BLUE 3 easy.txt
# Easy 3x3:       java -cp target/classes org.gerken.harmony.PuzzleGenerator 3 3 RED,BLUE,GREEN 8 easy.txt
# Medium 3x3:     java -cp target/classes org.gerken.harmony.PuzzleGenerator 3 3 RED,BLUE,GREEN 12 medium.txt
# Medium 4x4:     java -cp target/classes org.gerken.harmony.PuzzleGenerator 4 4 RED,BLUE,GREEN,YELLOW 10 medium.txt
# Hard 4x4:       java -cp target/classes org.gerken.harmony.PuzzleGenerator 4 4 RED,BLUE,GREEN,YELLOW 12 hard.txt

# Note: Puzzles with 15+ moves on 4x4 boards or any 5x5 puzzles may take very long to solve
```

### Solve a Puzzle

```bash
# Using convenience script (recommended)
./solve.sh puzzle.txt

# With custom options
./solve.sh -t 4 -r 10 puzzle.txt

# OR run directly with Java
java -cp target/classes org.gerken.harmony.HarmonySolver puzzle.txt

# After packaging:
java -jar target/harmony-solver-1.0-SNAPSHOT.jar puzzle.txt
```

### Command-Line Options

- `-t <threads>`: Number of worker threads (default: 2)
- `-r <seconds>`: Progress report interval in seconds (default: 30)
- `-c <threshold>`: Cache threshold for near-solution states (default: 4)

## Performance

Based on testing with depth-first search, intelligent move filtering, and invalidity tests:

| Puzzle Size | Moves | Solve Time | States Generated | Pruning Rate | Notes |
|-------------|-------|------------|------------------|--------------|-------|
| 2x2         | 3     | <1s        | 3-4              | 0% (optimal) | Only generates solution path |
| 3x3         | 10    | <1s        | ~36K             | ~31%         | 90% reduction from unfiltered |
| 4x4         | 8     | <1s        | ~30              | 0% (optimal) | Highly optimized path |
| 4x4         | 10+   | Minutes+   | Millions         | ~35%         | Complex, benefits from filtering |
| 5x5         | Varies| Extended   | Billions         | ~35-38%      | Very large search space |

**Optimization Impact**: Move filtering reduces generated states by up to 91% on complex puzzles by eliminating wasteful moves before they enter the search space. Queue sizes remain stable (300-1,200 states) even on difficult puzzles.

**Note**: Depth-first strategy combined with move filtering significantly reduces memory usage and focuses search on high-quality candidate moves.

## Documentation

- **[Architecture](docs/ARCHITECTURE.md)** - System design and technical approach
- **[Data Models](docs/DATA_MODELS.md)** - Core classes: Tile, Board, Move, BoardState
- **[Invalidity Tests](docs/INVALIDITY_TESTS.md)** - Pruning system for invalid board states
- **[Optimizations](docs/OPTIMIZATIONS.md)** - Move filtering and search optimizations (NEW)
- **[Development Guide](docs/DEVELOPMENT.md)** - Building, testing, and extending the solver
- **[Original Design](DESIGN.md)** - Initial design specifications
- **[Refactoring Notes](REFACTORING_NOTES.md)** - Recent code reorganization and optimizations
- **[Implementation Summary](IMPLEMENTATION_SUMMARY.md)** - Complete implementation notes for future sessions

## Key Features

- **Depth-First Search**: Multiple queues organized by move depth prevent memory explosion
- **Intelligent Move Filtering**: Eliminates wasteful moves before generation (up to 91% reduction)
  - Perfect swap detection: Forces optimal endgame moves
  - Last-move filtering: Prevents tiles from wasting final moves on non-productive swaps
- **Multi-threaded Processing**: Configurable thread pool for parallel state exploration
- **Thread-Local Caching**: Near-solution states cached per-thread to reduce queue contention (configurable threshold)
- **Smart Pruning**: 4 invalidity tests eliminate impossible states early
  - StuckTileTest: Detects rows with unsolvable tile configurations
  - WrongRowZeroMovesTest: Catches tiles stuck in wrong rows with no moves
  - BlockedSwapTest: Identifies tiles blocked from reaching target positions
  - IsolatedTileTest: Detects tiles with moves but no valid swap partners
- **Thread-Safe Design**: PendingStates container encapsulates all state management
- **Progress Reporting**: Configurable periodic status updates (default: 30 seconds)
- **Efficient Color Representation**: Integer-based colors for fast comparisons and low memory usage
- **Cached State Metrics**: Remaining moves cached in BoardState, computed once and decremented per move
- **Puzzle Generator**: Create guaranteed-solvable puzzles of any difficulty
- **Command-Line Interface**: No GUI dependencies, runs anywhere Java is available

## Input File Format

Puzzles use a simple text format with integer color IDs:

```
ROWS 3
COLS 3

# Color legend for human readability
COLORS
RED 0
BLUE 1
GREEN 2

TARGETS RED BLUE GREEN

# Tiles use color IDs for efficiency
TILES
A1 0 2
A2 1 2
A3 2 2
...
```

## Project Structure

```
harmony/
├── pom.xml                              # Maven project configuration
├── solve.sh                             # Convenience script to run solver
├── generate.sh                          # Convenience script to generate puzzles
├── README.md                            # This file
├── DESIGN.md                            # Original design
├── IMPLEMENTATION_SUMMARY.md            # Complete implementation notes
├── REFACTORING_NOTES.md                 # Recent code reorganization
├── docs/                                # Detailed documentation
│   ├── ARCHITECTURE.md                  # System architecture
│   ├── DATA_MODELS.md                   # Data structure documentation
│   ├── INVALIDITY_TESTS.md              # Invalidity test system
│   └── DEVELOPMENT.md                   # Development guide
├── puzzles/                             # Example puzzle files
│   ├── tiny.txt                         # 2x2 test puzzle
│   ├── simple.txt                       # 3x3 test puzzle
│   ├── easy.txt                         # Generated puzzles
│   ├── medium.txt
│   └── hard.txt
└── src/main/java/org/gerken/harmony/    # Source code
    ├── HarmonySolver.java               # Main solver application
    ├── PuzzleGenerator.java             # Puzzle generator utility
    ├── model/                           # Core data models (immutable)
    │   ├── Board.java
    │   ├── BoardState.java
    │   ├── Move.java
    │   └── Tile.java
    ├── logic/                           # Processing logic
    │   ├── BoardParser.java
    │   ├── ProgressReporter.java
    │   └── StateProcessor.java
    └── invalidity/                      # Pruning tests
        ├── InvalidityTest.java
        ├── InvalidityTestCoordinator.java
        ├── StuckTileTest.java
        ├── WrongRowZeroMovesTest.java
        ├── BlockedSwapTest.java
        └── IsolatedTileTest.java
```

## Technology Stack

- **Language**: Java 11+
- **Build Tool**: Maven
- **Concurrency**: `ConcurrentLinkedQueue`, thread pool executors
- **Architecture**: Thread-safe singletons, immutable data structures

## License

See [LICENSE](LICENSE) file for details.

## Contributing

This project is under active development. See [Development Guide](docs/DEVELOPMENT.md) for details on extending the solver.
