#!/usr/bin/env bash
set -euo pipefail

# Clawgic API smoke harness.
#
# Coverage:
# - Agent CRUD smoke: create + list + wallet filter + detail + leaderboard.
# - Tournament CRUD/status smoke: create + list + results index/detail.
# - Entry flow smoke with explicit x402 mode expectations:
#   - bypass mode (`x402.enabled=false`): entry request returns 201.
#   - challenge mode (`x402.enabled=true`): unpaid entry request returns 402.
#
# Mode controls:
# - X402_EXPECTED_MODE=auto      -> detect from runtime response (default)
# - X402_EXPECTED_MODE=bypass    -> require 201 entry behavior
# - X402_EXPECTED_MODE=challenge -> require 402 challenge behavior
#
# The script fails on any unexpected status and any 5xx response.

BASE_URL="${BASE_URL:-http://localhost:8080}"
WAIT_SECONDS="${WAIT_SECONDS:-60}"
X402_EXPECTED_MODE="${X402_EXPECTED_MODE:-auto}"
RUN_ID="${RUN_ID:-smoke-$(date -u +%Y%m%dT%H%M%SZ)-$$}"

PASS_COUNT=0
FAIL_COUNT=0
LAST_REQUEST_MATCHED_EXPECTED=false
RESPONSE_CODE=""
RESPONSE_BODY=""

SMOKE_AGENT_ID=""
SMOKE_TOURNAMENT_ID=""
SMOKE_WALLET_ADDRESS=""
DETECTED_X402_MODE=""

UNKNOWN_UUID="00000000-0000-0000-0000-000000000000"

log() {
  printf '[clawgic-smoke] %s\n' "$*"
}

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    printf 'Missing required command: %s\n' "${cmd}" >&2
    exit 1
  fi
}

validate_expected_mode() {
  case "${X402_EXPECTED_MODE}" in
    auto|bypass|challenge)
      ;;
    *)
      printf 'X402_EXPECTED_MODE must be one of: auto, bypass, challenge (got: %s)\n' \
        "${X402_EXPECTED_MODE}" >&2
      exit 1
      ;;
  esac
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

matches_expected_code() {
  local expected_codes="$1"
  local actual_code="$2"
  IFS='|' read -r -a expected_array <<< "${expected_codes}"
  for expected in "${expected_array[@]}"; do
    if [[ "${actual_code}" == "${expected}" ]]; then
      return 0
    fi
  done
  return 1
}

compact_body_preview() {
  printf '%s' "${RESPONSE_BODY}" | tr '\n' ' ' | sed 's/[[:space:]]\+/ /g' | cut -c1-220
}

record_pass() {
  local label="$1"
  local method="$2"
  local path="$3"
  local code="$4"
  PASS_COUNT=$((PASS_COUNT + 1))
  printf '[PASS] %-34s %s %s -> %s\n' "${label}" "${method}" "${path}" "${code}"
}

record_fail() {
  local label="$1"
  local method="$2"
  local path="$3"
  local expected="$4"
  local code="$5"
  local body_preview="$6"
  FAIL_COUNT=$((FAIL_COUNT + 1))
  printf '[FAIL] %-34s %s %s -> %s (expected %s)\n' "${label}" "${method}" "${path}" "${code}" "${expected}"
  if [[ -n "${body_preview}" ]]; then
    printf '       body: %s\n' "${body_preview}"
  fi
}

record_assertion_pass() {
  local label="$1"
  PASS_COUNT=$((PASS_COUNT + 1))
  printf '[PASS] %-34s ASSERT\n' "${label}"
}

record_assertion_fail() {
  local label="$1"
  local details="$2"
  FAIL_COUNT=$((FAIL_COUNT + 1))
  printf '[FAIL] %-34s ASSERT\n' "${label}"
  if [[ -n "${details}" ]]; then
    printf '       detail: %s\n' "${details}"
  fi
}

assert_request() {
  local label="$1"
  local method="$2"
  local path="$3"
  local expected_codes="$4"
  local body="${5:-}"
  shift 5 || true

  LAST_REQUEST_MATCHED_EXPECTED=false
  http_request "${method}" "${path}" "${body}" "$@"

  local body_preview
  body_preview="$(compact_body_preview)"

  if [[ "${RESPONSE_CODE}" =~ ^5[0-9][0-9]$ ]]; then
    record_fail "${label}" "${method}" "${path}" "${expected_codes}" "${RESPONSE_CODE}" "${body_preview}"
    return 0
  fi

  if ! matches_expected_code "${expected_codes}" "${RESPONSE_CODE}"; then
    record_fail "${label}" "${method}" "${path}" "${expected_codes}" "${RESPONSE_CODE}" "${body_preview}"
    return 0
  fi

  LAST_REQUEST_MATCHED_EXPECTED=true
  record_pass "${label}" "${method}" "${path}" "${RESPONSE_CODE}"
}

assert_body_contains() {
  local label="$1"
  local expected_fragment="$2"
  if [[ "${RESPONSE_BODY}" == *"${expected_fragment}"* ]]; then
    record_assertion_pass "${label}"
  else
    record_assertion_fail "${label}" "missing fragment: ${expected_fragment}"
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
    record_assertion_fail "${label}" "failed to read JSON path: ${path}"
    return
  fi

  if [[ "${actual}" == "${expected}" ]]; then
    record_assertion_pass "${label}"
  else
    record_assertion_fail "${label}" "path=${path}, expected=${expected}, actual=${actual}"
  fi
}

assert_json_length_equals() {
  local label="$1"
  local path="$2"
  local expected="$3"

  local actual
  if ! actual="$(response_json_len "${path}")"; then
    record_assertion_fail "${label}" "failed to compute JSON length for path: ${path}"
    return
  fi

  if [[ "${actual}" == "${expected}" ]]; then
    record_assertion_pass "${label}"
  else
    record_assertion_fail "${label}" "path=${path}, expected=${expected}, actual=${actual}"
  fi
}

wait_for_backend() {
  log "Waiting for ${BASE_URL}/actuator/health"
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

iso_utc_plus_minutes() {
  local minutes="$1"
  python3 - "${minutes}" <<'PY'
from datetime import datetime, timedelta, timezone
import sys

minutes = int(sys.argv[1])
value = datetime.now(timezone.utc) + timedelta(minutes=minutes)
print(value.isoformat().replace("+00:00", "Z"))
PY
}

run_static_checks() {
  assert_request "actuator-health" "GET" "/actuator/health" "200" ""
  assert_request "clawgic-health" "GET" "/api/clawgic/health" "200" ""

  assert_request "agents-list" "GET" "/api/clawgic/agents" "200" ""
  assert_request "agents-leaderboard" "GET" "/api/clawgic/agents/leaderboard?offset=0&limit=10" "200" ""

  assert_request "tournaments-list" "GET" "/api/clawgic/tournaments" "200" ""
  assert_request "tournaments-results-index" "GET" "/api/clawgic/tournaments/results" "200" ""

  assert_request "unknown-results-detail" "GET" "/api/clawgic/tournaments/${UNKNOWN_UUID}/results" "404" ""
  assert_request "unknown-bracket" "POST" "/api/clawgic/tournaments/${UNKNOWN_UUID}/bracket" "404" ""

  assert_request "matches-list" "GET" "/api/clawgic/matches" "200|404" ""

  assert_request "agent-create-validation" "POST" "/api/clawgic/agents" "400" '{}'
  assert_request "tournament-create-validation" "POST" "/api/clawgic/tournaments" "400" '{}'
}

run_agent_crud_checks() {
  SMOKE_WALLET_ADDRESS="$(wallet_for_run 'agent-wallet')"

  local payload
  payload="$(cat <<JSON
{"walletAddress":"${SMOKE_WALLET_ADDRESS}","name":"C53 Smoke Agent ${RUN_ID}","avatarUrl":"https://example.com/${RUN_ID}.png","systemPrompt":"Argue with concise, evidence-backed points.","skillsMarkdown":"- rebuttal\\n- synthesis","persona":"Analytical smoke profile","agentsMdSource":"# Smoke agent ${RUN_ID}","providerType":"MOCK","providerKeyRef":"smoke/${RUN_ID}","apiKey":"smoke-key-${RUN_ID}"}
JSON
)"

  assert_request "agent-create" "POST" "/api/clawgic/agents" "201" "${payload}"
  if [[ "${LAST_REQUEST_MATCHED_EXPECTED}" != "true" ]]; then
    return
  fi

  if SMOKE_AGENT_ID="$(response_json_get 'agentId' 2>/dev/null)" && [[ -n "${SMOKE_AGENT_ID}" ]]; then
    record_assertion_pass "agent-id-captured"
  else
    SMOKE_AGENT_ID=""
    record_assertion_fail "agent-id-captured" "could not parse agentId from create response"
    return
  fi

  assert_json_equals "agent-create-api-key-configured" "apiKeyConfigured" "true"
  assert_json_equals "agent-create-provider-type" "providerType" "MOCK"

  assert_request "agent-detail" "GET" "/api/clawgic/agents/${SMOKE_AGENT_ID}" "200" ""
  if [[ "${LAST_REQUEST_MATCHED_EXPECTED}" == "true" ]]; then
    assert_json_equals "agent-detail-id" "agentId" "${SMOKE_AGENT_ID}"
    assert_json_equals "agent-detail-wallet" "walletAddress" "${SMOKE_WALLET_ADDRESS}"
  fi

  assert_request "agents-list-wallet-filter" "GET" "/api/clawgic/agents?walletAddress=${SMOKE_WALLET_ADDRESS}" "200" ""
  if [[ "${LAST_REQUEST_MATCHED_EXPECTED}" == "true" ]]; then
    assert_body_contains "agents-list-contains-created-id" "${SMOKE_AGENT_ID}"
  fi

  assert_request "agents-leaderboard-after-create" "GET" "/api/clawgic/agents/leaderboard?offset=0&limit=25" "200" ""
}

run_tournament_crud_checks() {
  local start_time
  local entry_close_time
  start_time="$(iso_utc_plus_minutes 45)"
  entry_close_time="$(iso_utc_plus_minutes 15)"

  local payload
  payload="$(cat <<JSON
{"topic":"C53 smoke ${RUN_ID}: should x402 challenge mode be strict by default?","startTime":"${start_time}","entryCloseTime":"${entry_close_time}","baseEntryFeeUsdc":5.0}
JSON
)"

  assert_request "tournament-create" "POST" "/api/clawgic/tournaments" "201" "${payload}"
  if [[ "${LAST_REQUEST_MATCHED_EXPECTED}" != "true" ]]; then
    return
  fi

  if SMOKE_TOURNAMENT_ID="$(response_json_get 'tournamentId' 2>/dev/null)" && [[ -n "${SMOKE_TOURNAMENT_ID}" ]]; then
    record_assertion_pass "tournament-id-captured"
  else
    SMOKE_TOURNAMENT_ID=""
    record_assertion_fail "tournament-id-captured" "could not parse tournamentId from create response"
    return
  fi

  assert_json_equals "tournament-create-status" "status" "SCHEDULED"

  assert_request "tournaments-list-after-create" "GET" "/api/clawgic/tournaments" "200" ""
  if [[ "${LAST_REQUEST_MATCHED_EXPECTED}" == "true" ]]; then
    assert_body_contains "tournaments-list-contains-created-id" "${SMOKE_TOURNAMENT_ID}"
  fi

  assert_request "tournaments-results-index-after-create" "GET" "/api/clawgic/tournaments/results" "200" ""
  if [[ "${LAST_REQUEST_MATCHED_EXPECTED}" == "true" ]]; then
    assert_body_contains "results-index-contains-created-id" "${SMOKE_TOURNAMENT_ID}"
  fi

  assert_request "tournament-results-detail" "GET" "/api/clawgic/tournaments/${SMOKE_TOURNAMENT_ID}/results" "200" ""
  if [[ "${LAST_REQUEST_MATCHED_EXPECTED}" == "true" ]]; then
    assert_json_equals "tournament-results-status" "tournament.status" "SCHEDULED"
    assert_json_length_equals "tournament-results-matches-empty" "matches" "0"
    assert_json_length_equals "tournament-results-entries-initial" "entries" "0"
    assert_json_length_equals "tournament-results-settlement-empty" "settlement" "0"
  fi
}

assert_x402_mode_expectation() {
  if [[ "${X402_EXPECTED_MODE}" == "auto" ]]; then
    record_assertion_pass "x402-mode-auto-detected-${DETECTED_X402_MODE}"
    return
  fi

  if [[ "${X402_EXPECTED_MODE}" == "${DETECTED_X402_MODE}" ]]; then
    record_assertion_pass "x402-mode-expected-${X402_EXPECTED_MODE}"
  else
    record_assertion_fail "x402-mode-expected-${X402_EXPECTED_MODE}" "detected=${DETECTED_X402_MODE}"
  fi
}

run_lobby_eligibility_checks() {
  if [[ -z "${SMOKE_TOURNAMENT_ID}" ]]; then
    record_assertion_fail "lobby-eligibility-prerequisites" "missing tournament id"
    return
  fi

  # Verify lobby listing includes eligibility fields for the created tournament
  assert_request "lobby-eligibility-list" "GET" "/api/clawgic/tournaments" "200" ""
  if [[ "${LAST_REQUEST_MATCHED_EXPECTED}" != "true" ]]; then
    return
  fi

  assert_body_contains "lobby-has-canEnter-field" '"canEnter"'
  assert_body_contains "lobby-has-entryState-field" '"entryState"'
  assert_body_contains "lobby-has-currentEntries-field" '"currentEntries"'

  # Create a closed-window tournament and verify its eligibility
  local closed_start
  local closed_entry_close
  closed_start="$(iso_utc_plus_minutes 120)"
  closed_entry_close="$(iso_utc_plus_minutes -5)"

  local closed_payload
  closed_payload="$(cat <<JSON
{"topic":"C61 closed-window smoke ${RUN_ID}","startTime":"${closed_start}","entryCloseTime":"${closed_entry_close}","baseEntryFeeUsdc":5.0}
JSON
)"

  assert_request "closed-tournament-create" "POST" "/api/clawgic/tournaments" "201" "${closed_payload}"
  if [[ "${LAST_REQUEST_MATCHED_EXPECTED}" != "true" ]]; then
    return
  fi

  local closed_tournament_id
  if ! closed_tournament_id="$(response_json_get 'tournamentId' 2>/dev/null)" || [[ -z "${closed_tournament_id}" ]]; then
    record_assertion_fail "closed-tournament-id-captured" "could not parse tournamentId"
    return
  fi
  record_assertion_pass "closed-tournament-id-captured"

  # Verify lobby shows closed tournament as non-enterable
  assert_request "lobby-with-closed-tournament" "GET" "/api/clawgic/tournaments" "200" ""
  if [[ "${LAST_REQUEST_MATCHED_EXPECTED}" == "true" ]]; then
    assert_body_contains "lobby-contains-closed-tournament" "${closed_tournament_id}"
    assert_body_contains "lobby-contains-entry-window-closed-state" '"ENTRY_WINDOW_CLOSED"'
  fi
}

run_entry_and_bracket_checks() {
  if [[ -z "${SMOKE_TOURNAMENT_ID}" || -z "${SMOKE_AGENT_ID}" ]]; then
    record_assertion_fail "entry-flow-prerequisites" "missing tournament or agent id"
    return
  fi

  local payload
  payload="{\"agentId\":\"${SMOKE_AGENT_ID}\"}"

  assert_request "tournament-enter" "POST" "/api/clawgic/tournaments/${SMOKE_TOURNAMENT_ID}/enter" "201|402" "${payload}"
  if [[ "${LAST_REQUEST_MATCHED_EXPECTED}" != "true" ]]; then
    return
  fi

  if [[ "${RESPONSE_CODE}" == "201" ]]; then
    DETECTED_X402_MODE="bypass"
  elif [[ "${RESPONSE_CODE}" == "402" ]]; then
    DETECTED_X402_MODE="challenge"
  else
    DETECTED_X402_MODE="unknown"
  fi

  log "Detected x402 entry behavior mode: ${DETECTED_X402_MODE}"
  assert_x402_mode_expectation

  if [[ "${DETECTED_X402_MODE}" == "challenge" ]]; then
    assert_body_contains "x402-challenge-scheme" '"scheme":"x402"'
    assert_body_contains "x402-challenge-payment-header" '"paymentHeader":"X-PAYMENT"'

    assert_request "tournament-enter-malformed-payment" "POST" \
      "/api/clawgic/tournaments/${SMOKE_TOURNAMENT_ID}/enter" \
      "400" \
      "${payload}" \
      -H "X-PAYMENT: not-json"

    assert_request "unknown-tournament-enter" "POST" \
      "/api/clawgic/tournaments/${UNKNOWN_UUID}/enter" \
      "402" \
      "${payload}"
  fi

  if [[ "${DETECTED_X402_MODE}" == "bypass" ]]; then
    assert_json_equals "tournament-enter-agent-id" "agentId" "${SMOKE_AGENT_ID}"

    # Duplicate entry: assert conflict code is machine-readable
    assert_request "tournament-enter-duplicate" "POST" \
      "/api/clawgic/tournaments/${SMOKE_TOURNAMENT_ID}/enter" \
      "409" \
      "${payload}"
    if [[ "${LAST_REQUEST_MATCHED_EXPECTED}" == "true" ]]; then
      assert_json_equals "duplicate-entry-conflict-code" "code" "already_entered"
      assert_body_contains "duplicate-entry-has-message" '"message"'
    fi

    # Unknown tournament: assert structured error
    assert_request "unknown-tournament-enter" "POST" \
      "/api/clawgic/tournaments/${UNKNOWN_UUID}/enter" \
      "404" \
      "${payload}"

    # Invalid agent: assert structured 404 error code
    local invalid_agent_payload
    invalid_agent_payload="{\"agentId\":\"${UNKNOWN_UUID}\"}"
    assert_request "invalid-agent-enter" "POST" \
      "/api/clawgic/tournaments/${SMOKE_TOURNAMENT_ID}/enter" \
      "404" \
      "${invalid_agent_payload}"
    if [[ "${LAST_REQUEST_MATCHED_EXPECTED}" == "true" ]]; then
      assert_json_equals "invalid-agent-conflict-code" "code" "invalid_agent"
    fi
  fi

  assert_request "tournament-bracket-before-full" "POST" \
    "/api/clawgic/tournaments/${SMOKE_TOURNAMENT_ID}/bracket" \
    "409" \
    ""

  assert_request "tournament-results-after-entry" "GET" \
    "/api/clawgic/tournaments/${SMOKE_TOURNAMENT_ID}/results" \
    "200" \
    ""
  if [[ "${LAST_REQUEST_MATCHED_EXPECTED}" == "true" ]]; then
    assert_json_equals "tournament-results-after-entry-status" "tournament.status" "SCHEDULED"
    assert_json_length_equals "tournament-results-after-entry-matches" "matches" "0"

    if [[ "${DETECTED_X402_MODE}" == "bypass" ]]; then
      assert_json_length_equals "tournament-results-after-entry-count" "entries" "1"
    fi

    if [[ "${DETECTED_X402_MODE}" == "challenge" ]]; then
      assert_json_length_equals "tournament-results-after-entry-count" "entries" "0"
    fi
  fi
}

run_match_endpoint_checks() {
  # GET /api/clawgic/matches/{matchId} — not found for unknown match
  assert_request "match-detail-not-found" "GET" \
    "/api/clawgic/matches/${UNKNOWN_UUID}" \
    "404" \
    ""

  # GET /api/clawgic/tournaments/{tournamentId}/matches — for existing tournament
  if [[ -n "${SMOKE_TOURNAMENT_ID}" ]]; then
    assert_request "tournament-matches-list" "GET" \
      "/api/clawgic/tournaments/${SMOKE_TOURNAMENT_ID}/matches" \
      "200" \
      ""
    if [[ "${LAST_REQUEST_MATCHED_EXPECTED}" == "true" ]]; then
      # Verify matches list is a JSON array
      assert_body_contains "tournament-matches-list-is-array" '['
    fi
  fi

  # GET /api/clawgic/tournaments/{tournamentId}/matches — not found for unknown tournament
  assert_request "tournament-matches-not-found" "GET" \
    "/api/clawgic/tournaments/${UNKNOWN_UUID}/matches" \
    "404" \
    ""

  # GET /api/clawgic/tournaments/{tournamentId}/live — for existing tournament
  if [[ -n "${SMOKE_TOURNAMENT_ID}" ]]; then
    assert_request "tournament-live-status" "GET" \
      "/api/clawgic/tournaments/${SMOKE_TOURNAMENT_ID}/live" \
      "200" \
      ""
    if [[ "${LAST_REQUEST_MATCHED_EXPECTED}" == "true" ]]; then
      assert_body_contains "tournament-live-has-server-time" '"serverTime"'
      assert_body_contains "tournament-live-has-bracket" '"bracket"'
      assert_body_contains "tournament-live-has-tournament-id" '"tournamentId"'
      assert_body_contains "tournament-live-has-status" '"status"'
      assert_body_contains "tournament-live-has-start-time" '"startTime"'
      assert_body_contains "tournament-live-has-entry-close-time" '"entryCloseTime"'
      assert_body_contains "tournament-live-has-matches-completed" '"matchesCompleted"'

      # Verify tournament ID matches the queried ID
      local live_tournament_id
      live_tournament_id="$(response_json_get 'tournamentId')"
      if [[ "${live_tournament_id}" == "${SMOKE_TOURNAMENT_ID}" ]]; then
        record_pass "tournament-live-tournament-id-matches"
      else
        record_fail "tournament-live-tournament-id-matches" \
          "expected tournamentId=${SMOKE_TOURNAMENT_ID}, got ${live_tournament_id}"
      fi

      # Verify status is a valid tournament status
      local live_status
      live_status="$(response_json_get 'status')"
      case "${live_status}" in
        SCHEDULED|LOCKED|IN_PROGRESS|COMPLETED)
          record_pass "tournament-live-status-valid"
          ;;
        *)
          record_fail "tournament-live-status-valid" \
            "unexpected status: ${live_status}"
          ;;
      esac

      # Verify bracket is an array (may be empty for SCHEDULED)
      local bracket_len
      bracket_len="$(response_json_len 'bracket')"
      if [[ "${bracket_len}" =~ ^[0-9]+$ ]]; then
        record_pass "tournament-live-bracket-is-array"
      else
        record_fail "tournament-live-bracket-is-array" \
          "bracket is not an array or parse error: ${bracket_len}"
      fi
    fi
  fi

  # GET /api/clawgic/tournaments/{tournamentId}/live — not found for unknown tournament
  assert_request "tournament-live-not-found" "GET" \
    "/api/clawgic/tournaments/${UNKNOWN_UUID}/live" \
    "404" \
    ""

  # GET /api/clawgic/matches/{matchId} — detail for existing match (if bracket was created)
  if [[ -n "${SMOKE_TOURNAMENT_ID}" ]]; then
    # Fetch matches list to get a real match ID
    assert_request "match-fetch-for-detail-check" "GET" \
      "/api/clawgic/tournaments/${SMOKE_TOURNAMENT_ID}/matches" \
      "200" \
      ""
    if [[ "${LAST_REQUEST_MATCHED_EXPECTED}" == "true" ]]; then
      local match_count
      match_count="$(response_json_len '.')"
      if [[ "${match_count}" =~ ^[0-9]+$ ]] && [[ "${match_count}" -gt 0 ]]; then
        local first_match_id
        first_match_id="$(response_json_get '[0].matchId')"
        if [[ -n "${first_match_id}" ]] && [[ "${first_match_id}" != "null" ]]; then
          assert_request "match-detail-existing" "GET" \
            "/api/clawgic/matches/${first_match_id}" \
            "200" \
            ""
          if [[ "${LAST_REQUEST_MATCHED_EXPECTED}" == "true" ]]; then
            assert_body_contains "match-detail-has-match-id" '"matchId"'
            assert_body_contains "match-detail-has-status" '"status"'
            assert_body_contains "match-detail-has-transcript" '"transcriptJson"'
            assert_body_contains "match-detail-has-judgements" '"judgements"'
            assert_body_contains "match-detail-has-bracket-round" '"bracketRound"'
            assert_body_contains "match-detail-has-agent1" '"agent1Id"'
            assert_body_contains "match-detail-has-agent2" '"agent2Id"'

            # Verify match status is a valid status
            local match_status
            match_status="$(response_json_get 'status')"
            case "${match_status}" in
              SCHEDULED|IN_PROGRESS|PENDING_JUDGE|COMPLETED|FORFEITED)
                record_pass "match-detail-status-valid"
                ;;
              *)
                record_fail "match-detail-status-valid" \
                  "unexpected match status: ${match_status}"
                ;;
            esac
          fi
        fi
      fi
    fi
  fi
}

print_summary_and_exit() {
  printf '\nSummary: %s passed, %s failed\n' "${PASS_COUNT}" "${FAIL_COUNT}"
  if [[ "${FAIL_COUNT}" -gt 0 ]]; then
    exit 1
  fi
}

main() {
  require_cmd curl
  require_cmd python3
  validate_expected_mode

  log "Starting Clawgic smoke checks for ${BASE_URL}"
  log "x402 expected mode: ${X402_EXPECTED_MODE}"

  wait_for_backend
  run_static_checks
  run_agent_crud_checks
  run_tournament_crud_checks
  run_lobby_eligibility_checks
  run_entry_and_bracket_checks
  run_match_endpoint_checks
  print_summary_and_exit
}

main "$@"
