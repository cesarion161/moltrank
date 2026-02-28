#!/usr/bin/env bash
set -euo pipefail

# Clawgic API smoke harness (placeholder-safe).
#
# Expected status behavior during MVP build-out:
# - /actuator/health should be 200 when backend is up.
# - /api/clawgic/health should be 200 (Step C04 stub endpoint).
# - /api/clawgic/agents should be 200 (Step C16).
# - /api/clawgic/tournaments should be 200 (Step C17).
# - /api/clawgic/tournaments/results should be 200 (Step C45 results index).
# - /api/clawgic/tournaments/{id}/results should return 404 on unknown tournament.
# - /api/clawgic/tournaments/{id}/bracket returns 404 on unknown tournament (Step C19).
# - /api/clawgic/matches may be 404 until that API lands.
# - tournament entry POST may return 404 (unknown tournament) or 402 (x402 challenge path).
#
# The script fails on unexpected statuses and always treats any 5xx as a failure.

BASE_URL="${BASE_URL:-http://localhost:8080}"
WAIT_SECONDS="${WAIT_SECONDS:-60}"

PASS_COUNT=0
FAIL_COUNT=0
RESPONSE_CODE=""
RESPONSE_BODY=""

log() {
  printf '[clawgic-smoke] %s\n' "$*"
}

http_request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local output_file
  output_file="$(mktemp)"

  if [[ -n "${body}" ]]; then
    RESPONSE_CODE="$(curl -sS -o "${output_file}" -w "%{http_code}" \
      -X "${method}" \
      -H "Content-Type: application/json" \
      --data "${body}" \
      "${BASE_URL}${path}" || true)"
  else
    RESPONSE_CODE="$(curl -sS -o "${output_file}" -w "%{http_code}" \
      -X "${method}" \
      "${BASE_URL}${path}" || true)"
  fi

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

record_pass() {
  local label="$1"
  local method="$2"
  local path="$3"
  local code="$4"
  PASS_COUNT=$((PASS_COUNT + 1))
  printf '[PASS] %-26s %s %s -> %s\n' "${label}" "${method}" "${path}" "${code}"
}

record_fail() {
  local label="$1"
  local method="$2"
  local path="$3"
  local expected="$4"
  local code="$5"
  local body_preview="$6"
  FAIL_COUNT=$((FAIL_COUNT + 1))
  printf '[FAIL] %-26s %s %s -> %s (expected %s)\n' "${label}" "${method}" "${path}" "${code}" "${expected}"
  if [[ -n "${body_preview}" ]]; then
    printf '       body: %s\n' "${body_preview}"
  fi
}

assert_request() {
  local label="$1"
  local method="$2"
  local path="$3"
  local expected_codes="$4"
  local body="${5:-}"
  local body_preview

  http_request "${method}" "${path}" "${body}"
  body_preview="$(printf '%s' "${RESPONSE_BODY}" | tr '\n' ' ' | sed 's/[[:space:]]\+/ /g' | cut -c1-180)"

  if [[ "${RESPONSE_CODE}" =~ ^5[0-9][0-9]$ ]]; then
    record_fail "${label}" "${method}" "${path}" "${expected_codes}" "${RESPONSE_CODE}" "${body_preview}"
    return
  fi

  if ! matches_expected_code "${expected_codes}" "${RESPONSE_CODE}"; then
    record_fail "${label}" "${method}" "${path}" "${expected_codes}" "${RESPONSE_CODE}" "${body_preview}"
    return
  fi

  record_pass "${label}" "${method}" "${path}" "${RESPONSE_CODE}"
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

run_checks() {
  assert_request "actuator-health" "GET" "/actuator/health" "200"
  assert_request "clawgic-health" "GET" "/api/clawgic/health" "200"

  # Agent list endpoint is required from Step C16.
  assert_request "agents-list" "GET" "/api/clawgic/agents" "200"

  # Tournament list endpoint is required from Step C17.
  assert_request "tournaments-list" "GET" "/api/clawgic/tournaments" "200"
  assert_request "tournaments-results-index" "GET" "/api/clawgic/tournaments/results" "200"
  assert_request "tournaments-results-detail" "GET" \
    "/api/clawgic/tournaments/00000000-0000-0000-0000-000000000000/results" \
    "404"
  assert_request "tournaments-create-validation" "POST" "/api/clawgic/tournaments" "400" '{}'

  # Placeholder-safe list endpoints: absent routes are expected early in the plan.
  assert_request "matches-list" "GET" "/api/clawgic/matches" "200|404"

  # x402 entry route smoke check: unknown tournament should be 404, and
  # future protected-mode flows may respond 402 before controller execution.
  assert_request "tournament-enter" "POST" \
    "/api/clawgic/tournaments/00000000-0000-0000-0000-000000000000/enter" \
    "402|404" \
    '{"agentId":"00000000-0000-0000-0000-000000000000"}'

  # Bracket generation endpoint should be present after C19.
  assert_request "tournament-bracket" "POST" \
    "/api/clawgic/tournaments/00000000-0000-0000-0000-000000000000/bracket" \
    "404"
}

print_summary_and_exit() {
  printf '\nSummary: %s passed, %s failed\n' "${PASS_COUNT}" "${FAIL_COUNT}"
  if [[ "${FAIL_COUNT}" -gt 0 ]]; then
    exit 1
  fi
}

main() {
  wait_for_backend
  run_checks
  print_summary_and_exit
}

main "$@"
