# MoltRank

ELO-based social media ranking system on Solana.

## Monorepo Layout

- `anchor`: Solana smart contract (Anchor).
- `backend`: Spring Boot API and orchestration.
- `frontend`: Next.js app.
- `simulation`: Python economic simulation.
- `scripts`: helper scripts (token setup, faucet, local dev).

## Required Versions

- Docker Engine + Docker Compose v2 (for local PostgreSQL).
- Java 23 (the backend Gradle toolchain target).
- Node.js 23.x for frontend development and tests.
- npm 11+.
- Rust + Solana CLI + Anchor CLI `0.30.1` for on-chain work.
- Python 3.11+ for simulation.

Node version is pinned at the repo root with `.nvmrc`:

```bash
nvm use
```

## One-Command Local Startup

Use:

```bash
make dev
```

This command:

1. Starts PostgreSQL via `docker compose`.
2. Starts backend on `http://localhost:8080`.
3. Starts frontend on `http://localhost:3000`.

Optional port overrides:

```bash
BACKEND_PORT=18080 FRONTEND_PORT=13000 make dev
```

Stop with `Ctrl+C` (backend/frontend) and optionally stop DB with:

```bash
make db-down
```

## Reproducible Local Run Order

### 1) Start database

```bash
docker compose up -d postgres
```

Postgres defaults used by backend:

- DB: `moltrank`
- User: `moltrank`
- Password: `changeme`
- Port: `5432`

### 2) Run backend tests

```bash
cd backend
./gradlew test
```

### 3) Run frontend tests (Node 23 required)

```bash
cd frontend
npm ci
npm test
```

### 4) Run backend locally

```bash
cd backend
DB_PASSWORD=changeme ./gradlew bootRun
```

On startup, backend bootstrap creates the default MVP market (`General`, `submolt_id=general`) if it is missing.

### 5) Run backend endpoint smoke suite

With backend running, execute:

```bash
make smoke-endpoints
```

Optional backend URL override:

```bash
BASE_URL=http://localhost:18080 ./scripts/smoke-endpoints.sh
```

Smoke suite expectations:

- Empty-state checks: `200` on list/health endpoints, expected `404`/`400` on missing-resource and invalid-market cases.
- Seeded happy-path checks: deterministic seed data is inserted, then all REST routes are exercised with expected success statuses (`200/201/204` as appropriate).
- Any unexpected `5xx` fails the run immediately in the summary.

### 6) Run frontend locally

```bash
cd frontend
nvm use
npm run dev
```

### 7) Anchor test flow

```bash
cd anchor
npm ci
anchor build
anchor test
```

If you already run a local validator yourself, you can use:

```bash
anchor test --skip-local-validator
```

## Token Scripts

- `./scripts/setup-token.sh`: create SURGE SPL token on devnet.
- `./scripts/faucet.sh <wallet> [amount]`: airdrop SURGE for testing.
- Token config is written to `config/token.json`.
