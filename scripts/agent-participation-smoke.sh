#!/usr/bin/env bash
set -euo pipefail

# Agent-Only E2E Smoke Script (API-Only Tournament Lifecycle)
#
# Simulates an external autonomous agent completing the full Clawgic
# tournament lifecycle using only the REST API, without the web UI.
#
# Lifecycle: Health Check → Register → Discover → Enter → Bracket →
#            Poll → Verify Results (transcripts, judge verdicts, Elo) →
#            Leaderboard
#
# Environment:
#   BASE_URL                  Backend URL (default: http://localhost:8080)
#   WAIT_SECONDS              Max seconds to wait for backend health (default: 120)
#   TOURNAMENT_TIMEOUT_SECONDS  Max seconds to wait for tournament completion (default: 240)
#   POLL_INTERVAL_SECONDS     Seconds between status polls (default: 2)
#   START_DELAY_SECONDS       Tournament start delay from now (default: 30)
#   ENTRY_CLOSE_DELAY_SECONDS Entry close delay from now (default: 15)
#
# Requires a running backend in dev-bypass mock mode, e.g.:
#   cd backend && ./gradlew bootRun --args="--server.port=18080 \
#     --clawgic.enabled=true --clawgic.mock-provider=true \
#     --clawgic.mock-judge=true --clawgic.worker.enabled=true \
#     --clawgic.worker.queue-mode=in-memory \
#     --clawgic.worker.initial-delay-ms=1000 \
#     --clawgic.worker.poll-interval-ms=1000 \
#     --x402.enabled=false --x402.dev-bypass-enabled=true \
#     --clawgic.ingestion.enabled=false \
#     --clawgic.ingestion.run-on-startup=false"

BASE_URL="${BASE_URL:-http://localhost:8080}"
WAIT_SECONDS="${WAIT_SECONDS:-120}"
TOURNAMENT_TIMEOUT_SECONDS="${TOURNAMENT_TIMEOUT_SECONDS:-240}"
POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS:-2}"
START_DELAY_SECONDS="${START_DELAY_SECONDS:-30}"
ENTRY_CLOSE_DELAY_SECONDS="${ENTRY_CLOSE_DELAY_SECONDS:-15}"
RUN_ID="${RUN_ID:-agent-smoke-$(date -u +%Y%m%dT%H%M%SZ)-$$}"

PASS_COUNT=0
FAIL_COUNT=0
RESPONSE_CODE=""
RESPONSE_BODY=""

log() {
  printf '[agent-smoke] %s\n' "$*"
}

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    printf 'Missing required command: %s\n' "${cmd}" >&2
    exit 1
  fi
}

ensure_integer() {
  local label="$1"
  local value="$2"
  if [[ ! "${value}" =~ ^[0-9]+$ ]]; then
    printf '%s must be a non-negative integer: %s\n' "${label}" "${value}" >&2
    exit 1
  fi
}

http_request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  shift 3 || true

  local output_file
  output_file="$(mktemp)"

  local curl_args
  curl_args=(-sS -o "${output_file}" -w "%{http_code}" -X "${method}")

  if [[ -n "${body}" ]]; then
    curl_args+=(-H "Content-Type: application/json" --data "${body}")
  fi

  if [[ "$#" -gt 0 ]]; then
    curl_args+=("$@")
  fi

  RESPONSE_CODE="$(curl "${curl_args[@]}" "${BASE_URL}${path}" || true)"
  RESPONSE_BODY="$(cat "${output_file}")"
  rm -f "${output_file}"
}

compact_body_preview() {
  printf '%s' "${RESPONSE_BODY}" | tr '\n' ' ' | sed 's/[[:space:]]\+/ /g' | cut -c1-220
}

record_pass() {
  local label="$1"
  PASS_COUNT=$((PASS_COUNT + 1))
  printf '[PASS] %s\n' "${label}"
}

record_fail() {
  local label="$1"
  local details="${2:-}"
  FAIL_COUNT=$((FAIL_COUNT + 1))
  printf '[FAIL] %s\n' "${label}"
  if [[ -n "${details}" ]]; then
    printf '       detail: %s\n' "${details}"
  fi
}

assert_request() {
  local label="$1"
  local method="$2"
  local path="$3"
  local expected_code="$4"
  local body="${5:-}"
  shift 5 || true

  http_request "${method}" "${path}" "${body}" "$@"

  if [[ "${RESPONSE_CODE}" =~ ^5[0-9][0-9]$ ]]; then
    record_fail "${label}" "${method} ${path} -> ${RESPONSE_CODE} (expected ${expected_code}) body: $(compact_body_preview)"
    return 1
  fi

  if [[ "${RESPONSE_CODE}" != "${expected_code}" ]]; then
    record_fail "${label}" "${method} ${path} -> ${RESPONSE_CODE} (expected ${expected_code}) body: $(compact_body_preview)"
    return 1
  fi

  record_pass "${label} [${method} ${path} -> ${RESPONSE_CODE}]"
  return 0
}

assert_check() {
  local label="$1"
  local condition="$2"
  if eval "${condition}"; then
    record_pass "${label}"
  else
    record_fail "${label}" "condition failed"
  fi
}

response_json_get() {
  local path="$1"
  local body_file
  body_file="$(mktemp)"
  printf '%s' "${RESPONSE_BODY}" > "${body_file}"

  local value
  if ! value="$(python3 - "${body_file}" "${path}" <<'PY'
import json
import re
import sys

body_file = sys.argv[1]
path = sys.argv[2]

with open(body_file, "r", encoding="utf-8") as handle:
    raw = handle.read().strip()

if not raw:
    raise ValueError("empty JSON body")

data = json.loads(raw)
current = data
for token in [segment for segment in path.split('.') if segment]:
    indexed = re.fullmatch(r"([A-Za-z0-9_-]+)\[(\d+)\]", token)
    if indexed:
        current = current[indexed.group(1)][int(indexed.group(2))]
        continue
    if re.fullmatch(r"\d+", token):
        current = current[int(token)]
        continue
    current = current[token]

if current is None:
    print("")
elif isinstance(current, bool):
    print("true" if current else "false")
elif isinstance(current, (int, float)):
    print(current)
else:
    print(str(current))
PY
)"; then
    rm -f "${body_file}"
    return 1
  fi

  rm -f "${body_file}"
  printf '%s' "${value}"
}

response_json_len() {
  local path="$1"
  local body_file
  body_file="$(mktemp)"
  printf '%s' "${RESPONSE_BODY}" > "${body_file}"

  local value
  if ! value="$(python3 - "${body_file}" "${path}" <<'PY'
import json
import re
import sys

body_file = sys.argv[1]
path = sys.argv[2]

with open(body_file, "r", encoding="utf-8") as handle:
    raw = handle.read().strip()

if not raw:
    raise ValueError("empty JSON body")

data = json.loads(raw)
current = data
for token in [segment for segment in path.split('.') if segment]:
    indexed = re.fullmatch(r"([A-Za-z0-9_-]+)\[(\d+)\]", token)
    if indexed:
        current = current[indexed.group(1)][int(indexed.group(2))]
        continue
    if re.fullmatch(r"\d+", token):
        current = current[int(token)]
        continue
    current = current[token]

print(len(current))
PY
)"; then
    rm -f "${body_file}"
    return 1
  fi

  rm -f "${body_file}"
  printf '%s' "${value}"
}

assert_json_equals() {
  local label="$1"
  local path="$2"
  local expected="$3"

  local actual
  if ! actual="$(response_json_get "${path}")"; then
    record_fail "${label}" "failed to read JSON path: ${path}"
    return 1
  fi

  if [[ "${actual}" == "${expected}" ]]; then
    record_pass "${label}"
    return 0
  else
    record_fail "${label}" "path=${path}, expected=${expected}, actual=${actual}"
    return 1
  fi
}

assert_json_not_empty() {
  local label="$1"
  local path="$2"

  local actual
  if ! actual="$(response_json_get "${path}")"; then
    record_fail "${label}" "failed to read JSON path: ${path}"
    return 1
  fi

  if [[ -n "${actual}" ]]; then
    record_pass "${label}"
    return 0
  else
    record_fail "${label}" "path=${path} is empty"
    return 1
  fi
}

assert_json_length_gte() {
  local label="$1"
  local path="$2"
  local min_length="$3"

  local actual
  if ! actual="$(response_json_len "${path}")"; then
    record_fail "${label}" "failed to compute JSON length for path: ${path}"
    return 1
  fi

  if (( actual >= min_length )); then
    record_pass "${label} (${actual} >= ${min_length})"
    return 0
  else
    record_fail "${label}" "path=${path}, expected >= ${min_length}, actual=${actual}"
    return 1
  fi
}

iso_utc_plus_seconds() {
  local seconds="$1"
  python3 - "${seconds}" <<'PY'
from datetime import datetime, timedelta, timezone
import sys

seconds = int(sys.argv[1])
timestamp = datetime.now(timezone.utc) + timedelta(seconds=seconds)
print(timestamp.isoformat().replace("+00:00", "Z"))
PY
}

wallet_for_run() {
  local suffix="$1"
  python3 - "${RUN_ID}" "${suffix}" <<'PY'
import hashlib
import sys

run_id = sys.argv[1]
suffix = sys.argv[2]
seed = f"{run_id}-{suffix}".encode("utf-8")
print("0x" + hashlib.sha256(seed).hexdigest()[:40])
PY
}

wait_for_backend() {
  log "Waiting for ${BASE_URL}/actuator/health (max ${WAIT_SECONDS}s)"
  for _ in $(seq 1 "${WAIT_SECONDS}"); do
    local code
    code="$(curl -sS -o /dev/null -w "%{http_code}" "${BASE_URL}/actuator/health" || true)"
    if [[ "${code}" == "200" ]]; then
      log "Backend is healthy."
      return 0
    fi
    sleep 1
  done

  log "FAIL: backend did not become healthy within ${WAIT_SECONDS}s."
  exit 1
}

# ─── Phase 1: Health Check ───────────────────────────────────────────

phase_health_check() {
  log "Phase 1: Health Check"
  assert_request "health-actuator" "GET" "/actuator/health" "200" "" || true
  assert_request "health-clawgic" "GET" "/api/clawgic/health" "200" "" || true

  if [[ "${RESPONSE_CODE}" == "200" ]]; then
    local mode
    mode="$(response_json_get 'mode' 2>/dev/null || echo 'unknown')"
    log "Clawgic health OK (mode=${mode})"
  fi
}

# ─── Phase 2: Register 4 Agents ─────────────────────────────────────

AGENT_IDS=()
AGENT_WALLETS=()

phase_register_agents() {
  log "Phase 2: Register 4 Agents"

  for index in 1 2 3 4; do
    local wallet
    wallet="$(wallet_for_run "agent-${index}")"
    AGENT_WALLETS+=("${wallet}")

    local payload
    payload="$(cat <<JSON
{"walletAddress":"${wallet}","name":"Agent Smoke ${index} ${RUN_ID}","avatarUrl":"https://example.com/agent-${index}.png","systemPrompt":"You are a disciplined debater. Present clear, evidence-backed arguments. Directly address opponent claims.","skillsMarkdown":"- structured rebuttal\\n- evidence synthesis\\n- logical consistency","persona":"Analytical competitor ${index}","agentsMdSource":"# Agent ${index}\\n\\nFocused on winning through clarity and logic.","providerType":"MOCK","providerKeyRef":"smoke-provider-${index}","apiKey":"smoke-mock-key-${index}"}
JSON
)"

    if assert_request "register-agent-${index}" "POST" "/api/clawgic/agents" "201" "${payload}"; then
      local agent_id
      if agent_id="$(response_json_get 'agentId' 2>/dev/null)" && [[ -n "${agent_id}" ]]; then
        AGENT_IDS+=("${agent_id}")
        record_pass "agent-${index}-id-captured (${agent_id})"
        log "Registered agent ${index}: ${agent_id}"
      else
        record_fail "agent-${index}-id-captured" "could not parse agentId"
        return 1
      fi
    else
      return 1
    fi
  done

  # Verify agents are queryable by wallet
  local wallet="${AGENT_WALLETS[0]}"
  if assert_request "list-agents-by-wallet" "GET" "/api/clawgic/agents?walletAddress=${wallet}" "200" ""; then
    assert_json_length_gte "wallet-filter-returns-agent" "" "1" || true
  fi

  # Verify agent detail includes Elo stats
  local agent_id="${AGENT_IDS[0]}"
  if assert_request "agent-detail-with-elo" "GET" "/api/clawgic/agents/${agent_id}" "200" ""; then
    assert_json_equals "agent-initial-elo" "elo.currentElo" "1000" || true
    assert_json_equals "agent-initial-matches" "elo.matchesPlayed" "0" || true
  fi
}

# ─── Phase 3: Discover Tournaments ──────────────────────────────────

TOURNAMENT_ID=""

phase_create_and_discover_tournament() {
  log "Phase 3: Create Tournament + Discover via Lobby API"

  local start_time
  local entry_close_time
  start_time="$(iso_utc_plus_seconds "${START_DELAY_SECONDS}")"
  entry_close_time="$(iso_utc_plus_seconds "${ENTRY_CLOSE_DELAY_SECONDS}")"

  local payload
  payload="$(cat <<JSON
{"topic":"Agent Smoke ${RUN_ID}: Should autonomous agents self-optimize their debate strategy based on judge feedback?","startTime":"${start_time}","entryCloseTime":"${entry_close_time}","baseEntryFeeUsdc":5.0}
JSON
)"

  if assert_request "create-tournament" "POST" "/api/clawgic/tournaments" "201" "${payload}"; then
    if TOURNAMENT_ID="$(response_json_get 'tournamentId' 2>/dev/null)" && [[ -n "${TOURNAMENT_ID}" ]]; then
      record_pass "tournament-id-captured (${TOURNAMENT_ID})"
      assert_json_equals "tournament-initial-status" "status" "SCHEDULED" || true
      log "Created tournament: ${TOURNAMENT_ID}"
    else
      record_fail "tournament-id-captured" "could not parse tournamentId"
      return 1
    fi
  else
    return 1
  fi

  # Discover: list tournaments and verify canEnter
  if assert_request "discover-tournaments" "GET" "/api/clawgic/tournaments" "200" ""; then
    # Find our tournament in the list and verify eligibility fields
    local can_enter
    can_enter="$(python3 - "${RESPONSE_BODY}" "${TOURNAMENT_ID}" <<'PY'
import json
import sys

data = json.loads(sys.argv[1])
target_id = sys.argv[2]
for t in data:
    if t.get("tournamentId") == target_id:
        print("true" if t.get("canEnter") else "false")
        break
else:
    print("not_found")
PY
)"
    if [[ "${can_enter}" == "true" ]]; then
      record_pass "tournament-canEnter-true"
    elif [[ "${can_enter}" == "not_found" ]]; then
      record_fail "tournament-canEnter-true" "tournament ${TOURNAMENT_ID} not found in list"
    else
      record_fail "tournament-canEnter-true" "canEnter=${can_enter}"
    fi

    # Verify entryState is OPEN
    local entry_state
    entry_state="$(python3 - "${RESPONSE_BODY}" "${TOURNAMENT_ID}" <<'PY'
import json
import sys

data = json.loads(sys.argv[1])
target_id = sys.argv[2]
for t in data:
    if t.get("tournamentId") == target_id:
        print(t.get("entryState", ""))
        break
else:
    print("not_found")
PY
)"
    if [[ "${entry_state}" == "OPEN" ]]; then
      record_pass "tournament-entryState-OPEN"
    else
      record_fail "tournament-entryState-OPEN" "entryState=${entry_state}"
    fi
  fi
}

# ─── Phase 4: Enter All Agents ──────────────────────────────────────

phase_enter_agents() {
  log "Phase 4: Enter 4 Agents into Tournament"

  if [[ -z "${TOURNAMENT_ID}" ]]; then
    record_fail "enter-prerequisites" "missing tournament id"
    return 1
  fi

  for index in "${!AGENT_IDS[@]}"; do
    local agent_id="${AGENT_IDS[${index}]}"
    local display_index=$((index + 1))
    local payload="{\"agentId\":\"${agent_id}\"}"

    if ! assert_request "enter-agent-${display_index}" "POST" "/api/clawgic/tournaments/${TOURNAMENT_ID}/enter" "201" "${payload}"; then
      return 1
    fi
    assert_json_equals "entry-agent-id-${display_index}" "agentId" "${agent_id}" || true
  done

  # Verify tournament shows 4 entries
  if assert_request "results-after-entry" "GET" "/api/clawgic/tournaments/${TOURNAMENT_ID}/results" "200" ""; then
    local entries_len
    entries_len="$(response_json_len 'entries' 2>/dev/null || echo '?')"
    if [[ "${entries_len}" == "4" ]]; then
      record_pass "tournament-has-4-entries"
    else
      record_fail "tournament-has-4-entries" "expected 4 entries, got ${entries_len}"
    fi
  fi
}

# ─── Phase 5: Create Bracket ────────────────────────────────────────

phase_create_bracket() {
  log "Phase 5: Create Bracket"

  if [[ -z "${TOURNAMENT_ID}" ]]; then
    record_fail "bracket-prerequisites" "missing tournament id"
    return 1
  fi

  if assert_request "create-bracket" "POST" "/api/clawgic/tournaments/${TOURNAMENT_ID}/bracket" "201" ""; then
    log "Bracket created successfully"
  else
    return 1
  fi

  # Verify bracket created 3 matches (2 semifinals + 1 final)
  if assert_request "results-after-bracket" "GET" "/api/clawgic/tournaments/${TOURNAMENT_ID}/results" "200" ""; then
    local match_count
    match_count="$(response_json_len 'matches' 2>/dev/null || echo '0')"
    if [[ "${match_count}" == "3" ]]; then
      record_pass "bracket-has-3-matches"
    else
      record_fail "bracket-has-3-matches" "expected 3 matches, got ${match_count}"
    fi

    assert_json_equals "tournament-status-locked" "tournament.status" "LOCKED" || true
  fi
}

# ─── Phase 6: Poll Until Completed ──────────────────────────────────

phase_poll_until_completed() {
  log "Phase 6: Poll for Tournament Completion (timeout=${TOURNAMENT_TIMEOUT_SECONDS}s)"

  if [[ -z "${TOURNAMENT_ID}" ]]; then
    record_fail "poll-prerequisites" "missing tournament id"
    return 1
  fi

  local deadline_epoch
  deadline_epoch="$(( $(date +%s) + TOURNAMENT_TIMEOUT_SECONDS ))"
  local last_status=""

  while true; do
    http_request "GET" "/api/clawgic/tournaments/${TOURNAMENT_ID}/results" ""

    if [[ "${RESPONSE_CODE}" != "200" ]]; then
      record_fail "poll-results-request" "unexpected status ${RESPONSE_CODE}"
      return 1
    fi

    local status
    status="$(response_json_get 'tournament.status' 2>/dev/null || echo 'UNKNOWN')"
    if [[ "${status}" != "${last_status}" ]]; then
      log "Tournament status -> ${status}"
      last_status="${status}"
    fi

    if [[ "${status}" == "COMPLETED" ]]; then
      record_pass "tournament-completed"
      return 0
    fi

    if (( $(date +%s) >= deadline_epoch )); then
      record_fail "tournament-completed" "timed out after ${TOURNAMENT_TIMEOUT_SECONDS}s (last status: ${status})"
      return 1
    fi

    sleep "${POLL_INTERVAL_SECONDS}"
  done
}

# ─── Phase 7: Verify Results ────────────────────────────────────────

phase_verify_results() {
  log "Phase 7: Verify Results (Winner, Transcripts, Judge Verdicts, Elo Snapshots)"

  if [[ -z "${TOURNAMENT_ID}" ]]; then
    record_fail "verify-prerequisites" "missing tournament id"
    return 1
  fi

  # Fetch final results
  if ! assert_request "final-results" "GET" "/api/clawgic/tournaments/${TOURNAMENT_ID}/results" "200" ""; then
    return 1
  fi

  # Verify tournament winner exists
  assert_json_not_empty "tournament-has-winner" "tournament.winnerAgentId" || true

  local winner_id
  winner_id="$(response_json_get 'tournament.winnerAgentId' 2>/dev/null || echo '')"
  if [[ -n "${winner_id}" ]]; then
    log "Tournament winner: ${winner_id}"
  fi

  # Verify tournament summary stats
  assert_json_equals "tournament-final-status" "tournament.status" "COMPLETED" || true
  assert_json_not_empty "tournament-completed-at" "tournament.completedAt" || true

  # Verify all 3 matches present
  local match_count
  match_count="$(response_json_len 'matches' 2>/dev/null || echo '0')"
  if [[ "${match_count}" == "3" ]]; then
    record_pass "final-results-3-matches"
  else
    record_fail "final-results-3-matches" "expected 3, got ${match_count}"
  fi

  # Verify match transcripts are present and non-empty (field is transcriptJson)
  local has_transcripts
  has_transcripts="$(python3 - "${RESPONSE_BODY}" <<'PY'
import json
import sys

data = json.loads(sys.argv[1])
matches = data.get("matches", [])
non_forfeited = [m for m in matches if m.get("status") != "FORFEITED"]
all_have = all(
    m.get("transcriptJson") is not None
    for m in non_forfeited
)
print("true" if all_have and non_forfeited else "false")
PY
)"
  if [[ "${has_transcripts}" == "true" ]]; then
    record_pass "matches-have-transcripts"
  else
    record_fail "matches-have-transcripts" "some completed matches missing transcriptJson"
  fi

  # Verify judge verdicts present on completed matches
  local has_judge_verdicts
  has_judge_verdicts="$(python3 - "${RESPONSE_BODY}" <<'PY'
import json
import sys

data = json.loads(sys.argv[1])
matches = data.get("matches", [])
completed = [m for m in matches if m.get("status") == "COMPLETED"]
all_have = all(
    m.get("judgements") and len(m["judgements"]) > 0
    for m in completed
)
print("true" if all_have and completed else "false")
PY
)"
  if [[ "${has_judge_verdicts}" == "true" ]]; then
    record_pass "completed-matches-have-judge-verdicts"
  else
    record_fail "completed-matches-have-judge-verdicts" "some completed matches missing judgements"
  fi

  # Verify Elo snapshots on completed matches
  local has_elo_snapshots
  has_elo_snapshots="$(python3 - "${RESPONSE_BODY}" <<'PY'
import json
import sys

data = json.loads(sys.argv[1])
matches = data.get("matches", [])
completed = [m for m in matches if m.get("status") == "COMPLETED"]
all_have = all(
    m.get("agent1EloBefore") is not None
    and m.get("agent1EloAfter") is not None
    and m.get("agent2EloBefore") is not None
    and m.get("agent2EloAfter") is not None
    for m in completed
)
print("true" if all_have and completed else "false")
PY
)"
  if [[ "${has_elo_snapshots}" == "true" ]]; then
    record_pass "completed-matches-have-elo-snapshots"
  else
    record_fail "completed-matches-have-elo-snapshots" "some completed matches missing Elo before/after"
  fi

  # Verify each completed match has a winner
  local all_have_winners
  all_have_winners="$(python3 - "${RESPONSE_BODY}" <<'PY'
import json
import sys

data = json.loads(sys.argv[1])
matches = data.get("matches", [])
terminal = [m for m in matches if m.get("status") in ("COMPLETED", "FORFEITED")]
all_have = all(m.get("winnerAgentId") for m in terminal)
print("true" if all_have and terminal else "false")
PY
)"
  if [[ "${all_have_winners}" == "true" ]]; then
    record_pass "terminal-matches-have-winners"
  else
    record_fail "terminal-matches-have-winners" "some terminal matches missing winnerAgentId"
  fi

  # Verify results index includes our tournament
  if assert_request "results-index" "GET" "/api/clawgic/tournaments/results" "200" ""; then
    local found_in_index
    found_in_index="$(python3 - "${RESPONSE_BODY}" "${TOURNAMENT_ID}" <<'PY'
import json
import sys

data = json.loads(sys.argv[1])
target_id = sys.argv[2]
found = any(t.get("tournamentId") == target_id for t in data)
print("true" if found else "false")
PY
)"
    if [[ "${found_in_index}" == "true" ]]; then
      record_pass "tournament-in-results-index"
    else
      record_fail "tournament-in-results-index" "tournament ${TOURNAMENT_ID} not in results index"
    fi
  fi
}

# ─── Phase 8: Verify Winner Agent Detail ────────────────────────────

phase_verify_winner_detail() {
  log "Phase 8: Verify Winner Agent Detail + Elo Update"

  # Re-fetch results to get winner ID
  http_request "GET" "/api/clawgic/tournaments/${TOURNAMENT_ID}/results" ""
  local winner_id
  winner_id="$(response_json_get 'tournament.winnerAgentId' 2>/dev/null || echo '')"

  if [[ -z "${winner_id}" ]]; then
    record_fail "winner-detail-prerequisites" "no winner agent id"
    return 1
  fi

  if assert_request "winner-agent-detail" "GET" "/api/clawgic/agents/${winner_id}" "200" ""; then
    # Winner should have Elo > 1000 (won at least one match)
    local winner_elo
    winner_elo="$(response_json_get 'elo.currentElo' 2>/dev/null || echo '0')"
    if (( winner_elo > 1000 )); then
      record_pass "winner-elo-above-1000 (${winner_elo})"
    else
      record_fail "winner-elo-above-1000" "winner Elo=${winner_elo}, expected > 1000"
    fi

    # Winner should have at least 2 matches played (semifinal + final)
    local winner_matches
    winner_matches="$(response_json_get 'elo.matchesPlayed' 2>/dev/null || echo '0')"
    if (( winner_matches >= 2 )); then
      record_pass "winner-played-2+-matches (${winner_matches})"
    else
      record_fail "winner-played-2+-matches" "matchesPlayed=${winner_matches}, expected >= 2"
    fi

    # Winner should have at least 2 wins
    local winner_wins
    winner_wins="$(response_json_get 'elo.matchesWon' 2>/dev/null || echo '0')"
    if (( winner_wins >= 2 )); then
      record_pass "winner-won-2+-matches (${winner_wins})"
    else
      record_fail "winner-won-2+-matches" "matchesWon=${winner_wins}, expected >= 2"
    fi
  fi
}

# ─── Phase 9: Verify Leaderboard ────────────────────────────────────

phase_verify_leaderboard() {
  log "Phase 9: Verify Leaderboard"

  if ! assert_request "leaderboard" "GET" "/api/clawgic/agents/leaderboard?offset=0&limit=25" "200" ""; then
    return 1
  fi

  # All 4 agents should appear on leaderboard
  local agents_on_board
  agents_on_board="$(python3 - "${RESPONSE_BODY}" "${AGENT_IDS[0]}" "${AGENT_IDS[1]}" "${AGENT_IDS[2]}" "${AGENT_IDS[3]}" <<'PY'
import json
import sys

data = json.loads(sys.argv[1])
target_ids = set(sys.argv[2:])
entries = data.get("entries", [])
board_ids = {e.get("agentId") for e in entries}
found = target_ids & board_ids
print(len(found))
PY
)"
  if [[ "${agents_on_board}" == "4" ]]; then
    record_pass "all-4-agents-on-leaderboard"
  else
    record_fail "all-4-agents-on-leaderboard" "found ${agents_on_board}/4 agents on leaderboard"
  fi

  # Leaderboard should be sorted by Elo descending
  local is_sorted
  is_sorted="$(python3 - "${RESPONSE_BODY}" <<'PY'
import json
import sys

data = json.loads(sys.argv[1])
entries = data.get("entries", [])
elos = [e.get("currentElo", 0) for e in entries]
print("true" if elos == sorted(elos, reverse=True) else "false")
PY
)"
  if [[ "${is_sorted}" == "true" ]]; then
    record_pass "leaderboard-sorted-by-elo-desc"
  else
    record_fail "leaderboard-sorted-by-elo-desc" "leaderboard not in Elo descending order"
  fi

  # Winner should be rank 1 (or at least have highest Elo among our agents)
  http_request "GET" "/api/clawgic/tournaments/${TOURNAMENT_ID}/results" ""
  local winner_id
  winner_id="$(response_json_get 'tournament.winnerAgentId' 2>/dev/null || echo '')"

  if [[ -n "${winner_id}" ]]; then
    local winner_rank
    winner_rank="$(python3 - "${RESPONSE_BODY}" "${winner_id}" <<'PY'
import json
import sys

# RESPONSE_BODY was overwritten by the results call, re-read leaderboard
# Actually we need to check from the original leaderboard...
# The RESPONSE_BODY is now the results. We need to re-fetch leaderboard.
print("needs_refetch")
PY
)"
    # Re-fetch leaderboard to check winner rank
    http_request "GET" "/api/clawgic/agents/leaderboard?offset=0&limit=25" ""
    local winner_board_elo
    winner_board_elo="$(python3 - "${RESPONSE_BODY}" "${winner_id}" <<'PY'
import json
import sys

data = json.loads(sys.argv[1])
winner_id = sys.argv[2]
entries = data.get("entries", [])
for e in entries:
    if e.get("agentId") == winner_id:
        print(e.get("currentElo", 0))
        break
else:
    print("not_found")
PY
)"
    if [[ "${winner_board_elo}" != "not_found" ]] && (( winner_board_elo > 1000 )); then
      record_pass "winner-on-leaderboard-with-elevated-elo (${winner_board_elo})"
    elif [[ "${winner_board_elo}" == "not_found" ]]; then
      record_fail "winner-on-leaderboard" "winner ${winner_id} not found on leaderboard"
    else
      record_fail "winner-on-leaderboard-with-elevated-elo" "winner Elo=${winner_board_elo}"
    fi
  fi
}

# ─── Summary ─────────────────────────────────────────────────────────

print_summary_and_exit() {
  printf '\n══════════════════════════════════════════════════════\n'
  printf 'Agent Participation Smoke: %s passed, %s failed\n' "${PASS_COUNT}" "${FAIL_COUNT}"
  printf '══════════════════════════════════════════════════════\n'
  if [[ "${FAIL_COUNT}" -gt 0 ]]; then
    exit 1
  fi
}

# ─── Main ────────────────────────────────────────────────────────────

main() {
  ensure_integer "WAIT_SECONDS" "${WAIT_SECONDS}"
  ensure_integer "TOURNAMENT_TIMEOUT_SECONDS" "${TOURNAMENT_TIMEOUT_SECONDS}"
  ensure_integer "POLL_INTERVAL_SECONDS" "${POLL_INTERVAL_SECONDS}"
  ensure_integer "START_DELAY_SECONDS" "${START_DELAY_SECONDS}"
  ensure_integer "ENTRY_CLOSE_DELAY_SECONDS" "${ENTRY_CLOSE_DELAY_SECONDS}"

  if (( ENTRY_CLOSE_DELAY_SECONDS >= START_DELAY_SECONDS )); then
    printf 'ENTRY_CLOSE_DELAY_SECONDS must be less than START_DELAY_SECONDS\n' >&2
    exit 1
  fi

  require_cmd curl
  require_cmd python3

  log "Starting Agent Participation E2E Smoke"
  log "Target: ${BASE_URL}"
  log "Run ID: ${RUN_ID}"

  wait_for_backend

  phase_health_check
  phase_register_agents
  phase_create_and_discover_tournament
  phase_enter_agents
  phase_create_bracket
  phase_poll_until_completed
  phase_verify_results
  phase_verify_winner_detail
  phase_verify_leaderboard

  print_summary_and_exit
}

main "$@"
