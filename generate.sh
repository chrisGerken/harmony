#!/bin/bash

# Harmony Puzzle Generator - Convenience script
# Runs the PuzzleGenerator with compiled classes from target/classes

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Set the classpath to the compiled classes
CLASSPATH="${SCRIPT_DIR}/target/classes"

# Check if classes are compiled
if [ ! -d "$CLASSPATH" ]; then
    echo "Error: Compiled classes not found at $CLASSPATH"
    echo "Please compile the project first:"
    echo "  mvn compile"
    echo "  OR"
    echo "  javac -d target/classes -sourcepath src/main/java src/main/java/org/gerken/harmony/*.java src/main/java/org/gerken/harmony/model/*.java src/main/java/org/gerken/harmony/logic/*.java src/main/java/org/gerken/harmony/invalidity/*.java"
    exit 1
fi

# Check if correct number of arguments provided
if [ $# -ne 5 ]; then
    echo "Harmony Puzzle Generator"
    echo ""
    echo "Usage: $0 <rows> <cols> <color1,color2,...> <num_moves> <output_file>"
    echo ""
    echo "Arguments:"
    echo "  rows          Number of rows in the puzzle"
    echo "  cols          Number of columns in the puzzle"
    echo "  colors        Comma-separated list of color names (e.g., RED,BLUE,GREEN)"
    echo "  num_moves     Number of scramble moves (difficulty)"
    echo "  output_file   Path to output puzzle file"
    echo ""
    echo "Examples:"
    echo "  $0 3 3 RED,BLUE,GREEN 10 puzzle.txt"
    echo "  $0 4 4 RED,BLUE,GREEN,YELLOW 8 easy.txt"
    echo ""
    echo "Recommended difficulty levels:"
    echo "  Easy:   2x2 with 3-5 moves, 3x3 with 5-8 moves"
    echo "  Medium: 3x3 with 10-15 moves, 4x4 with 8-12 moves"
    echo "  Hard:   4x4 with 12-15 moves"
    exit 1
fi

# Run the generator with all provided arguments
java -cp "$CLASSPATH" org.gerken.harmony.PuzzleGenerator "$@"
