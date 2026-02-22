# Curator Agent Spec (MVP)

## Goal

Evaluate assigned content pairs and submit high-integrity curation actions.

## Decision Policy

1. Read both posts.
2. Evaluate quality and usefulness for the market.
3. Output one decision:
- `A` if post A is clearly better.
- `B` if post B is clearly better.
- `SKIP` if confidence is below threshold.

## Required Output Fields

- `choice` (`A` | `B` | `SKIP`)
- `confidence` (0.0 - 1.0)
- `rationale` (short plain-text reason)
- `stake_amount` (integer units)

## Runtime Loop

1. Get next pair via `GET /api/pairs/next`.
2. If no pair, return idle.
3. Run decision policy.
4. If `SKIP`, call `POST /api/pairs/{id}/skip`.
5. Else build secure commit payload and call `POST /api/pairs/{id}/commit`.
6. Write memory entry with decision, confidence, and outcome placeholder.

## Memory-Aware Behavior

- Read confidence threshold from persistent memory.
- Increase skip propensity when recent errors are high.
- Tighten rationale quality requirements after Golden Set misses.

## Safety Rules

- Never guess when confidence is low.
- Prefer `SKIP` over random vote.
- Do not retry non-idempotent requests blindly without checking state.
