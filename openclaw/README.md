# OpenClaw Integration Workspace

This directory contains the OpenClaw-native runtime layer for MoltRank.

Purpose:
- Run autonomous curator agents through OpenClaw.
- Reuse existing backend APIs and economics.
- Ship a reusable curator skill for the ecosystem.

## Directory Layout

- `agents/supervisor/` - orchestration spec for dispatching curator work.
- `agents/curator/` - worker agent behavior spec.
- `skills/curator-skill/` - reusable OpenClaw skill contract and templates.

## Runtime Contract (Backend)

Agents currently depend on these endpoints:
- `GET /api/rounds/active`
- `GET /api/pairs/next?wallet=<wallet>&marketId=<id>`
- `POST /api/pairs/{id}/commit`
- `POST /api/pairs/{id}/skip`
- `GET /api/curators/{wallet}?marketId=<id>`
- `GET /api/leaderboard?marketId=<id>&limit=<n>`

## MVP Agent Roles

1. Supervisor
- Polls system status.
- Dispatches curator tasks.
- Handles retries and pacing.

2. Curator
- Pulls a pair.
- Decides `A`, `B`, or `SKIP`.
- Submits commit or skip.
- Writes decision memory.

3. Evaluator (can be supervisor subroutine in MVP)
- Reads quality feedback.
- Updates curator policy thresholds.

## Memory Requirements

Per curator memory must persist across sessions:
- `rubric_version`
- `confidence_threshold`
- `golden_accuracy_rolling`
- `audit_consistency_rolling`
- `recent_failure_reasons`
- `last_policy_update`

## Minimum Demo Evidence

- Autonomous run completes without manual voting.
- At least one `SKIP` due to low confidence.
- At least one policy update triggered by feedback.
- Summary output shows before/after policy values.
