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

# Default memory setting
MEMORY="4G"

# Parse solve.sh arguments (extract -m before passing rest to Java)
ARGS=()
while [[ $# -gt 0 ]]; do
    case $1 in
        -m)
            if [[ -n "$2" && ! "$2" =~ ^- ]]; then
                MEMORY="$2"
                shift 2
            else
                echo "Error: -m requires a memory value (e.g., -m 8G)"
                exit 1
            fi
            ;;
        *)
            ARGS+=("$1")
            shift
            ;;
    esac
done

# Run the solver with all remaining arguments
java -Xms${MEMORY} -Xmx${MEMORY} -cp "$JAR_FILE" org.gerken.harmony.HarmonySolver "${ARGS[@]}"
