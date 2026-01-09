#!/bin/bash

# Harmony Test Builder - Convenience script
# Runs the TestBuilder using harmony-solver.jar

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

# Check if correct number of arguments provided
if [ $# -ne 1 ]; then
    echo "Harmony Test Builder"
    echo ""
    echo "Builds test states by starting from a solved puzzle and applying moves backwards."
    echo ""
    echo "Usage: $0 <test-spec-file>"
    echo ""
    echo "Arguments:"
    echo "  test-spec-file   Path to test specification file"
    echo ""
    echo "If the file does not exist, an empty template will be created."
    echo ""
    echo "Examples:"
    echo "  $0 my_test.spec          # Create template or build from spec"
    echo "  $0 tests/stuck_tile.spec # Build test case from specification"
    exit 1
fi

# Run the test builder with the provided argument
java -cp "$JAR_FILE" org.gerken.harmony.TestBuilder "$@"
