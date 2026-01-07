#!/bin/bash

# Harmony Puzzle Solver - Convenience script
# Runs the HarmonySolver with compiled classes from target/classes

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

# Run the solver with all provided arguments
java -cp "$CLASSPATH" org.gerken.harmony.HarmonySolver "$@"
