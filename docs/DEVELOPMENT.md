# Development Guide

[← Back to README](../README.md)

## Table of Contents

- [Getting Started](#getting-started)
- [Project Structure](#project-structure)
- [Building](#building)
- [Input File Format](#input-file-format)
- [Configuration](#configuration)
- [Extending the Solver](#extending-the-solver)
- [Testing](#testing)
- [Debugging](#debugging)
- [Performance Tuning](#performance-tuning)
- [Future Enhancements](#future-enhancements)

## Getting Started

### Prerequisites

- **Java**: JDK 11 or higher
- **Maven**: 3.6+ for building
- **IDE**: IntelliJ IDEA, Eclipse, or VS Code with Java extensions (recommended)

### Initial Setup

```bash
# Clone the repository
git clone <repository-url>
cd harmony

# Compile the project
mvn compile

# Run tests (when implemented)
mvn test

# Package as JAR (when main class is implemented)
mvn package
```

## Project Structure

```
harmony/
├── pom.xml                              # Maven configuration
├── README.md                            # Project overview
├── DESIGN.md                            # Original design doc
├── docs/                                # Documentation
│   ├── ARCHITECTURE.md                  # System architecture
│   ├── DATA_MODELS.md                   # Core data structures
│   ├── INVALIDITY_TESTS.md              # Pruning system
│   └── DEVELOPMENT.md                   # This file
├── src/
│   ├── main/
│   │   ├── java/org/gerken/harmony/
│   │   │   ├── InvalidityTest.java              # Test interface
│   │   │   ├── InvalidityTestCoordinator.java   # Test coordinator
│   │   │   ├── TooManyMovesTest.java            # Pruning tests
│   │   │   ├── ImpossibleColorAlignmentTest.java
│   │   │   ├── InsufficientMovesTest.java
│   │   │   ├── Tile.java                        # Data models
│   │   │   ├── Board.java
│   │   │   ├── BoardState.java
│   │   │   ├── Move.java
│   │   │   ├── BoardParser.java                 # To be implemented
│   │   │   ├── StateProcessor.java              # To be implemented
│   │   │   ├── ProgressReporter.java            # To be implemented
│   │   │   └── HarmonySolver.java               # Main class (TBI)
│   │   └── resources/
│   │       └── example-puzzles/                 # Sample input files
│   └── test/
│       └── java/org/gerken/harmony/
│           ├── TileTest.java                    # Unit tests (TBI)
│           ├── BoardTest.java
│           ├── InvalidityTestsTest.java
│           └── IntegrationTest.java
└── target/                                      # Build output (git-ignored)
```

## Building

### Maven Commands

```bash
# Clean build artifacts
mvn clean

# Compile source code
mvn compile

# Run tests
mvn test

# Package as executable JAR
mvn package

# Skip tests during packaging
mvn package -DskipTests

# Install to local Maven repository
mvn install
```

### IDE Setup

#### IntelliJ IDEA

1. Open project: File → Open → Select `harmony` directory
2. Wait for Maven import to complete
3. Set JDK: File → Project Structure → Project → SDK: 11+

#### Eclipse

1. Import project: File → Import → Maven → Existing Maven Projects
2. Select `harmony` directory
3. Right-click project → Properties → Java Build Path → Set JRE

#### VS Code

1. Install "Extension Pack for Java"
2. Open `harmony` folder
3. Maven will auto-configure

## Input File Format

### Implemented Format

The input file uses a simple text format with integer color IDs for efficiency:

```
# Example puzzle file

ROWS 3
COLS 3

# Color mapping (legend for human readability)
COLORS
RED 0
BLUE 1
GREEN 2

# Target colors for each row (uses color names)
TARGETS RED BLUE GREEN

# Tile configuration (uses color IDs)
# Format: Position ColorID Moves
TILES
A1 0 3
A2 1 2
A3 2 1
B1 2 3
B2 0 2
B3 1 1
C1 1 3
C2 2 2
C3 0 1
```

### Key Design Points

1. **COLORS Section**: Maps color names to integer IDs (serves as legend)
2. **TARGETS Section**: Uses color **names** for readability
3. **TILES Section**: Uses color **IDs** for efficiency
4. **Comments**: Lines starting with `#` are ignored
5. **Position Format**: Row letter + column number (e.g., A1, C5)

### Color Representation Rationale

Colors are stored as integers internally:
- **Memory**: 4 bytes vs 16+ bytes for String
- **Speed**: `==` comparison ~10x faster than `.equals()`
- **Hashing**: Direct integer hashing is more efficient

Input files maintain human-readable color names in the COLORS section while using efficient integer IDs in the bulk data (TILES).

### Parser Implementation

**Implemented**: `BoardParser.java`

```java
public class BoardParser {
    /**
     * Parses a puzzle file and creates the initial BoardState.
     * Maps color names to integer IDs using COLORS section.
     */
    public static BoardState parse(String filename) throws IOException {
        // Reads COLORS section to build color name → ID mapping
        // Parses TARGETS using color names
        // Parses TILES using color IDs
        // Returns BoardState with integer-based colors
    }
}
```

## Puzzle Generator Utility

### Overview

`PuzzleGenerator` creates **guaranteed-solvable** puzzles by working backwards from a solved state:

1. Starts with a solved board (all tiles match row colors, 0 moves)
2. Makes N random moves in reverse (swaps tiles and increments move counts)
3. Outputs the scrambled state as a puzzle file

This ensures every generated puzzle has a solution!

### Usage

```bash
java -cp target/classes org.gerken.harmony.PuzzleGenerator <rows> <cols> <colors> <moves> <output>
```

**Arguments:**
- `<rows>` - Number of rows (must equal number of colors)
- `<cols>` - Number of columns
- `<colors>` - Comma-separated color names (e.g., RED,BLUE,GREEN)
- `<moves>` - Number of random moves to scramble (higher = harder)
- `<output>` - Output file path

### Examples

```bash
# Easy 2x2 puzzle with 5 moves
java -cp target/classes org.gerken.harmony.PuzzleGenerator 2 2 RED,BLUE 5 puzzles/easy.txt

# Medium 3x3 puzzle with 15 moves
java -cp target/classes org.gerken.harmony.PuzzleGenerator 3 3 RED,BLUE,GREEN 15 puzzles/medium.txt

# Hard 4x4 puzzle with 30 moves
java -cp target/classes org.gerken.harmony.PuzzleGenerator 4 4 RED,BLUE,GREEN,YELLOW 30 puzzles/hard.txt

# Very hard 5x5 puzzle with 50 moves
java -cp target/classes org.gerken.harmony.PuzzleGenerator 5 5 RED,BLUE,GREEN,YELLOW,PURPLE 50 puzzles/veryhard.txt
```

### Algorithm

```
1. Create solved board:
   - Each row has all tiles of its target color
   - All tiles have 0 remaining moves

2. For N moves:
   - Randomly choose row or column
   - Randomly select two positions
   - Swap tiles and increment both move counts

3. Write resulting board to file
```

The puzzle is guaranteed solvable in at most N moves (optimal solution may be shorter).

## Configuration

### HarmonySolver Command-Line

**Implemented**:

```bash
java -cp target/classes org.gerken.harmony.HarmonySolver [options] <puzzle-file>

# Or after packaging:
java -jar target/harmony-solver-1.0-SNAPSHOT.jar [options] <puzzle-file>

Options:
  -t, --threads <N>    Number of worker threads (default: CPU cores)
  -r, --report <N>     Progress report interval in seconds (default: 30)
  -h, --help           Show help message

Examples:
  # Run with defaults
  java -cp target/classes org.gerken.harmony.HarmonySolver puzzle.txt

  # 8 threads, 10-second reports
  java -cp target/classes org.gerken.harmony.HarmonySolver -t 8 -r 10 puzzle.txt
```

## Extending the Solver

### Adding a New Invalidity Test

See [Invalidity Tests - Adding New Tests](INVALIDITY_TESTS.md#adding-new-tests) for detailed guide.

Quick summary:

1. Create class implementing `InvalidityTest`
2. Use singleton pattern
3. Implement `isInvalid()` and `getName()`
4. Register in `InvalidityTestCoordinator`
5. Test thoroughly!

Example:

```java
public class MinimumDistanceTest implements InvalidityTest {
    private static final MinimumDistanceTest INSTANCE = new MinimumDistanceTest();

    private MinimumDistanceTest() {}

    public static MinimumDistanceTest getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isInvalid(BoardState boardState) {
        // Check if any tile cannot reach its target position
        // with its remaining moves
        return false;  // Implement logic
    }

    @Override
    public String getName() {
        return "MinimumDistanceTest";
    }
}
```

### Adding a New Data Model

If extending the data model (e.g., adding metadata to `Board`):

1. Maintain immutability
2. Implement `equals()` and `hashCode()`
3. Update constructors to handle new fields
4. Document thread-safety implications

### Implementing the Main Solver

**To be implemented**: `HarmonySolver.java`

```java
public class HarmonySolver {
    public static void main(String[] args) {
        // 1. Parse command-line arguments
        // 2. Load and parse input file
        // 3. Create initial BoardState
        // 4. Initialize ConcurrentLinkedQueue
        // 5. Start worker threads
        // 6. Start progress reporter thread
        // 7. Wait for solution or exhaustion
        // 8. Output results
    }
}
```

### Implementing State Processor

**To be implemented**: `StateProcessor.java`

```java
public class StateProcessor implements Runnable {
    private final ConcurrentLinkedQueue<BoardState> queue;
    private final AtomicBoolean solutionFound;

    @Override
    public void run() {
        while (!solutionFound.get()) {
            BoardState state = queue.poll();
            if (state == null) continue;

            if (state.isSolved()) {
                // Output solution
                solutionFound.set(true);
                return;
            }

            // Generate and queue valid successor states
            for (Move move : generateMoves(state)) {
                BoardState next = state.applyMove(move);
                if (!InvalidityTestCoordinator.getInstance().isInvalid(next)) {
                    queue.add(next);
                }
            }
        }
    }

    private List<Move> generateMoves(BoardState state) {
        // Generate all possible moves
        return moves;
    }
}
```

## Testing

### Unit Tests

Test individual components in isolation:

```java
@Test
public void testTileDecrement() {
    Tile tile = new Tile("RED", 5);
    Tile decremented = tile.decrementMoves();

    assertEquals("RED", decremented.getColor());
    assertEquals(4, decremented.getRemainingMoves());
    assertEquals(5, tile.getRemainingMoves());  // Original unchanged
}

@Test
public void testInvalidityTest() {
    // Create a board state that should be invalid
    Board board = createInvalidBoard();
    BoardState state = new BoardState(board);

    assertTrue(TooManyMovesTest.getInstance().isInvalid(state));
}
```

### Integration Tests

Test the complete solving process:

```java
@Test
public void testSimplePuzzle() {
    // Load a simple puzzle with known solution
    BoardState initial = BoardParser.parse("test-puzzles/simple.txt");

    // Run solver
    List<Move> solution = solve(initial);

    // Verify solution
    assertNotNull(solution);
    assertTrue(solution.size() > 0);

    // Apply moves and verify solved
    BoardState final = applyMoves(initial, solution);
    assertTrue(final.isSolved());
}
```

### Test Puzzle Library

Create `src/main/resources/test-puzzles/` with:

- `simple-1.txt`: 2×2 board, 1 move solution
- `medium-1.txt`: 3×3 board, 5 move solution
- `complex-1.txt`: 4×4 board, 20+ move solution
- `unsolvable-1.txt`: Board with no solution

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=BoardTest

# Run specific test method
mvn test -Dtest=BoardTest#testSwap

# Run with verbose output
mvn test -X
```

## Debugging

### Logging

Add logging framework (e.g., SLF4J + Logback):

```xml
<!-- Add to pom.xml -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.0.9</version>
</dependency>
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.4.11</version>
</dependency>
```

Usage:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StateProcessor implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(StateProcessor.class);

    public void run() {
        log.debug("Processing state: {}", state);
        log.info("Solution found in {} moves", state.getMoveCount());
    }
}
```

### Debugging Pruning

To verify pruning isn't too aggressive:

```java
// Track pruning statistics
AtomicLong totalStates = new AtomicLong();
AtomicLong prunedStates = new AtomicLong();

// In processor
totalStates.incrementAndGet();
if (coordinator.isInvalid(state)) {
    prunedStates.incrementAndGet();
}

// Report
double pruneRate = 100.0 * prunedStates.get() / totalStates.get();
System.out.printf("Pruned %.1f%% of states\n", pruneRate);
```

### Visualizing Board States

Helper method for debugging:

```java
public class BoardPrinter {
    public static void print(Board board) {
        System.out.println("Board:");
        for (int r = 0; r < board.getRowCount(); r++) {
            System.out.print((char)('A' + r) + " ");
            for (int c = 0; c < board.getColumnCount(); c++) {
                Tile tile = board.getTile(r, c);
                System.out.printf("%s:%-2d ", tile.getColor(), tile.getRemainingMoves());
            }
            System.out.printf(" → %s\n", board.getRowTargetColor(r));
        }
    }
}
```

### Debugging Deadlocks

If threads hang:

```bash
# Get thread dump
jstack <pid>

# Or use jconsole
jconsole
```

Look for:
- Threads waiting on `queue.poll()`
- Deadlocks in synchronization

## Performance Tuning

### Profiling

Use JVM profiling tools:

```bash
# Enable profiling
java -agentlib:hprof=cpu=samples,depth=10 -jar harmony-solver.jar puzzle.txt

# Use JProfiler, VisualVM, or YourKit
```

### Optimization Checklist

- [ ] Order invalidity tests by speed (fastest first)
- [ ] Use primitive types where possible (avoid boxing)
- [ ] Minimize object allocations in hot paths
- [ ] Consider object pooling for frequently created objects
- [ ] Profile and optimize bottlenecks
- [ ] Tune thread pool size
- [ ] Optimize queue threshold for offloading

### Thread Pool Tuning

```java
// Too few threads: CPUs idle
// Too many threads: Context switching overhead

// Recommended starting point
int threads = Runtime.getRuntime().availableProcessors();

// For I/O-heavy workloads (filesystem offloading)
int threads = Runtime.getRuntime().availableProcessors() * 2;
```

### Memory Tuning

```bash
# Increase heap size
java -Xmx4G -jar harmony-solver.jar puzzle.txt

# Monitor GC
java -XX:+PrintGCDetails -Xmx4G -jar harmony-solver.jar puzzle.txt
```

## Future Enhancements

### Planned Features

1. **State Deduplication**
   - Track visited board configurations
   - Avoid reprocessing duplicate states
   - Use hash set with custom `Board.hashCode()`

2. **Heuristic Search (A\*)**
   - Priority queue instead of FIFO
   - Heuristic: estimate moves needed to solution
   - Process promising states first

3. **Bidirectional Search**
   - Search forward from start and backward from goal
   - Meet in the middle

4. **Distributed Computing**
   - Split work across multiple machines
   - Requires serialization and network communication

5. **GUI Visualization**
   - Real-time display of search progress
   - Visualize solution playback

6. **Puzzle Generator**
   - Generate random solvable puzzles
   - Difficulty levels

### Enhancement Ideas

- **Adaptive Thread Pool**: Dynamically adjust thread count
- **Smart Offloading**: Offload oldest/least-promising states first
- **Solution Optimizer**: Find shortest solution
- **Statistics Dashboard**: Detailed performance metrics
- **Puzzle Validator**: Verify puzzle is solvable before starting

## Coding Standards

### Java Style

- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Use 4-space indentation
- Maximum line length: 100 characters
- Always use braces for control structures

### Documentation

- Javadoc for all public classes and methods
- Explain **why**, not just **what**
- Include examples for complex APIs

### Git Commits

- Use conventional commit format: `type(scope): message`
- Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`
- Examples:
  - `feat(pruning): add minimum distance invalidity test`
  - `fix(board): correct swap method for edge positions`
  - `docs(readme): update installation instructions`

## Getting Help

### Resources

- [Architecture Documentation](ARCHITECTURE.md)
- [Data Models Documentation](DATA_MODELS.md)
- [Invalidity Tests Documentation](INVALIDITY_TESTS.md)
- [Original Design Document](../DESIGN.md)

### Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Make changes and test thoroughly
4. Commit with descriptive messages
5. Push and create a pull request

### Questions?

- Check documentation in `docs/` directory
- Review existing code for patterns
- Ask for clarification on design decisions

[← Back to README](../README.md)
