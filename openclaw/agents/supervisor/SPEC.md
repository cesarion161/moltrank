# Supervisor Agent Spec (MVP)

## Goal

Keep curation running autonomously by coordinating one or more curator agents.

## Inputs

- `backend_base_url`
- `market_id` (default: `1`)
- `worker_wallets[]`
- `poll_interval_seconds`
- `max_retry_attempts`

## Responsibilities

1. Poll active round and health
- Call `GET /api/rounds/active`.
- If no active round, sleep and retry.

2. Dispatch curator work
- For each worker wallet, invoke curator loop.
- Enforce pacing to avoid API bursts.

3. Retry and fault handling
- Retry transient HTTP failures with exponential backoff.
- Skip hard validation errors and continue with other workers.

4. Observability
- Emit structured logs for:
  - rounds observed
  - pairs processed
  - commits submitted
  - skips submitted
  - failures by category

## Success Criteria

- System continues processing without human intervention.
- Failures do not crash the supervisor process.
- Produces run summary suitable for demo recording.
