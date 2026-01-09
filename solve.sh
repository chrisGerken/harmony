#!/bin/bash

# Harmony Puzzle Solver - Convenience script
# Runs the HarmonySolver using harmony-solver.jar

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Set the classpath to the jar file
JAR_FILE="${SCRIPT_DIR}/target/harmony-solver.jar"

# Check if jar exists
if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found at $JAR_FILE"
    echo "Please build the project first:"
    echo "  mvn package"
    exit 1
fi

# Run the solver with all provided arguments
java -Xms6G -Xmx12G -cp "$JAR_FILE" org.gerken.harmony.HarmonySolver "$@"
