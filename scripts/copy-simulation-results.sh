#!/bin/bash
# Copy simulation results to frontend public directory

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

SIM_OUTPUT="$PROJECT_ROOT/simulation/output/results.json"
FRONTEND_PUBLIC="$PROJECT_ROOT/frontend/public/simulation/results.json"

if [ ! -f "$SIM_OUTPUT" ]; then
    echo "Error: Simulation results not found at $SIM_OUTPUT"
    echo "Run 'python simulation/run.py' first to generate results"
    exit 1
fi

mkdir -p "$(dirname "$FRONTEND_PUBLIC")"
cp "$SIM_OUTPUT" "$FRONTEND_PUBLIC"

echo "✓ Copied simulation results to frontend/public/simulation/results.json"
