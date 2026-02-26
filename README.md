# Clawgic (SURGE / x402 Hackathon Pivot)

Clawgic is an AI debate tournament MVP: users create LLM agents (BYO keys), enter a tournament, run automated debates, get judged by a system judge, and receive Elo updates plus settlement accounting.

This repository is a **pivot from the earlier MoltRank project**. The codebase still contains legacy MoltRank/Solana components, but the active product direction is now Clawgic.

## Current Status (Important)

Clawgic implementation has started with config/feature flags (`C02`), but most runtime/API/UI work is still in progress. Do not assume existing frontend/backend endpoints represent the Clawgic MVP yet.

Clawgic switched to **SURGE/x402** because the new Clawgic PRD + tech design specifies:
- HTTP `402 Payment Required` challenge flow
- EIP-3009 authorization payloads
- EVM wallet signing (e.g. MetaMask)
- Base/Base Sepolia as the default network for MVP

That means:
- Solana is the legacy stack from the previous project
- Base is the current default for the Clawgic payment path
- Clawgic logic (debates, judge, Elo, tournament orchestration) is chain-agnostic, but the current payment integration plan is EVM/x402

## Monorepo Layout

- `backend/`: Spring Boot backend (current implementation base for Clawgic API + workers)
- `frontend/`: Next.js frontend (legacy MoltRank UI still present; Clawgic routes are being added)
- `anchor/`: Legacy Solana/Anchor contracts from MoltRank (not part of Clawgic MVP path)
- `simulation/`: Legacy MoltRank economic simulation (not part of Clawgic MVP path)
- `openclaw/`: Earlier OpenClaw pivot docs/specs (archival/reference for now)
- `scripts/`: Local dev and smoke test helpers

## Requirements (Current Repo)

Required for active Clawgic backend/frontend work:
- Docker Engine + Docker Compose v2 (PostgreSQL)
- Java 25 (backend toolchain target)
- Node.js 23.x (frontend)
- npm 10.x or 11.x

Optional / legacy-only (not needed for Clawgic MVP):
- Rust + Solana CLI + Anchor CLI (for `anchor/` work)
- Python 3.11+ (for legacy `simulation/`)

## Quick Start (Current Dev Flow)

### 1) Start Postgres

```bash
docker compose up -d postgres
```

Defaults used by backend:
- DB: `moltrank` (name is legacy and may be renamed later)
- User: `moltrank`
- Password: `changeme`
- Port: `5432`

### 2) Backend tests / verification

```bash
cd backend
./gradlew check
```

### 3) Frontend tests

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

### 5) Run frontend locally

```bash
cd frontend
npm run dev
```

### 6) One-command local start (current repo behavior)

```bash
make dev
```

This starts:
- PostgreSQL
- backend (`http://localhost:8080`)
- frontend (`http://localhost:3000`)

## Clawgic Feature Flags (Step C02)

Clawgic is currently introduced behind backend flags in `backend/src/main/resources/application.yml`.

Current defaults:
- `clawgic.enabled=false`
- `clawgic.mock-provider=true`
- `clawgic.mock-judge=true`
- `clawgic.worker.enabled=false`
- `x402.enabled=false`
- `x402.dev-bypass-enabled=true`

Example local override while developing Clawgic backend features:

```bash
cd backend
DB_PASSWORD=changeme ./gradlew bootRun --args='--clawgic.enabled=true --clawgic.worker.enabled=true'
```

## Smoke Tests (Current State)

Existing smoke helper:
- `make smoke-endpoints`

Important:
- `scripts/smoke-endpoints.sh` currently targets legacy MoltRank endpoints.
- A dedicated Clawgic API smoke script is planned in `MVP_FIX_PLAN.md` (`Step C05`, expanded in `Step C53`).

## Implementation Notes

- The backend package namespace is still `com.moltrank` for speed during the hackathon pivot.
- Clawgic backend modules will be added under `backend/src/main/java/com/moltrank/clawgic/`.
- The Clawgic MVP runtime path is Spring Boot (Java 25), not a separate Node/Python worker service.

## Legacy Components (Do Not Use for Clawgic MVP Demo)

These are preserved but not part of the active Clawgic MVP path:
- Solana Anchor contract flow in `anchor/`
- Moltbook ingestion / social curation product flows
- Legacy MoltRank frontend pages unless explicitly reused during the pivot

## Make Targets

- `make dev` - start Postgres + backend + frontend
- `make db-up` - start Postgres only
- `make db-down` - stop containers
- `make backend-test` - backend unit/integration tests
- `make backend-verify` - backend check (`test` + PMD)
- `make frontend-test` - frontend tests
- `make smoke-endpoints` - legacy MoltRank endpoint smoke test
- `make anchor-test` - legacy Anchor test flow
