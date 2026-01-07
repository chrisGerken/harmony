# Harmony Puzzle Solver

A multi-threaded Java application that solves color-matching tile puzzles using breadth-first search with intelligent pruning.

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
# Generate a 3x3 puzzle with 10 random moves
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
# Run with default settings (2 threads, 30s report interval)
java -cp target/classes org.gerken.harmony.HarmonySolver puzzle.txt

# Use more threads if needed
java -cp target/classes org.gerken.harmony.HarmonySolver -t 4 -r 10 puzzle.txt

# After packaging:
java -jar target/harmony-solver-1.0-SNAPSHOT.jar -t 2 puzzle.txt
```

### Command-Line Options

- `-t <threads>`: Number of worker threads (default: 2)
- `-r <seconds>`: Progress report interval in seconds (default: 30)

## Performance

Based on testing with current invalidity tests:

| Puzzle Size | Moves | Solve Time | States Processed | Pruning Rate |
|-------------|-------|------------|------------------|--------------|
| 2x2         | 3     | <1s        | ~1,000           | ~60%         |
| 3x3         | 10    | 3s         | ~850,000         | 64%          |
| 4x4         | 8     | 1s         | ~50,000          | 40%          |

**Note**: Complexity grows exponentially with move count. Puzzles with 15+ moves on 4x4 boards may require very long solve times (minutes to hours) or may be intractable.

## Documentation

- **[Architecture](docs/ARCHITECTURE.md)** - System design and technical approach
- **[Data Models](docs/DATA_MODELS.md)** - Core classes: Tile, Board, Move, BoardState
- **[Invalidity Tests](docs/INVALIDITY_TESTS.md)** - Pruning system for invalid board states
- **[Development Guide](docs/DEVELOPMENT.md)** - Building, testing, and extending the solver
- **[Original Design](DESIGN.md)** - Initial design specifications

## Key Features

- **Multi-threaded Processing**: Configurable thread pool for parallel state exploration
- **Intelligent Pruning**: Multiple invalidity tests eliminate impossible states early
- **Thread-Safe Design**: Singleton pattern with `ConcurrentLinkedQueue` for state management
- **Progress Reporting**: Configurable periodic status updates (default: 30 seconds)
- **Efficient Color Representation**: Integer-based colors for fast comparisons and low memory usage
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
├── DESIGN.md                            # Original + implementation notes
├── README.md                            # This file
├── docs/                                # Detailed documentation
│   ├── ARCHITECTURE.md                  # System architecture
│   ├── DATA_MODELS.md                   # Data structure documentation
│   ├── INVALIDITY_TESTS.md              # Invalidity test system
│   └── DEVELOPMENT.md                   # Development guide
├── puzzles/                             # Example puzzle files
│   ├── tiny.txt                         # 2x2 test puzzle
│   ├── simple.txt                       # 3x3 test puzzle
│   ├── easy.txt                         # Generated 2x2
│   ├── medium.txt                       # Generated 4x4
│   └── hard.txt                         # Generated 5x5
└── src/main/java/org/gerken/harmony/    # Source code
    ├── HarmonySolver.java               # Main solver application
    ├── PuzzleGenerator.java             # Puzzle generator utility
    ├── BoardParser.java                 # Input file parser
    ├── StateProcessor.java              # Worker thread
    ├── ProgressReporter.java            # Progress monitoring
    ├── InvalidityTest.java              # Test interface
    ├── InvalidityTestCoordinator.java   # Test coordinator
    ├── StuckTileTest.java               # Pruning tests
    ├── WrongRowZeroMovesTest.java
    ├── BlockedSwapTest.java
    ├── Tile.java                        # Core data models
    ├── Board.java
    ├── BoardState.java
    └── Move.java
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
