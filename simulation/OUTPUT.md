# Simulation Output Generation

## Running the Full Simulation

To generate the complete simulation results with all 7 charts:

```bash
# From project root
python simulation/run.py
```

This will:
- Run 10,000+ rounds across multiple scenarios
- Generate PNG charts in `simulation/output/`
- Export data to `simulation/output/results.json`
- Take approximately 5-10 minutes to complete

## Copying Results to Frontend

After the simulation completes, copy the results to the frontend:

```bash
# From project root
./scripts/copy-simulation-results.sh
```

Or manually:

```bash
cp simulation/output/results.json frontend/public/simulation/results.json
```

## Testing the Frontend

The frontend includes sample data for development. To test with real simulation data:

1. Run the simulation: `python simulation/run.py`
2. Copy results: `./scripts/copy-simulation-results.sh`
3. Start frontend: `cd frontend && npm run dev`
4. Open http://localhost:3000/simulation

## Chart Details

The simulation generates 7 charts:

1. **GlobalPool Balance Over Time** - 10K rounds showing solvency
2. **Cumulative PnL by Agent Type** - Performance hierarchy
3. **New Market Bootstrap Curve** - Curator attraction over time
4. **Alpha Sensitivity** - Impact of alpha parameter
5. **Minority Loss Sensitivity** - Effect on consensus
6. **Audit Pair Detection Rate** - Detection of lazy/bot curators
7. **Feed Quality / ELO Stability** - Quality across scenarios

## Sample Data

The frontend includes minimal sample data at `frontend/public/simulation/results.json` for testing. This should be replaced with real simulation output for production use.
