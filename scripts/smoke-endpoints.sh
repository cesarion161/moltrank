#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"

SEED_MARKET_ID=910101
SEED_ROUND_ID=910201
SEED_POST_A_ID=910301
SEED_POST_B_ID=910302
SEED_PAIR_ID=910401
SEED_IDENTITY_ID=910501
SEED_GOLDEN_SET_ID=910601
SEED_CURATOR_WALLET="smoke-curator-wallet-910101"
SEED_READER_WALLET="smoke-reader-wallet-910101"
MISSING_WALLET="smoke-missing-wallet-910101"
MISSING_AGENT="smoke-missing-agent-910101"
EMPTY_LINK_WALLET="smoke-empty-$(date +%s)"

PASS_COUNT=0
FAIL_COUNT=0
RESPONSE_CODE=""
RESPONSE_BODY=""

log() {
  printf '[smoke] %s\n' "$*"
}

wait_for_backend() {
  log "Waiting for backend health at ${BASE_URL}/actuator/health"
  for _ in $(seq 1 60); do
    local code
    code="$(curl -sS -o /dev/null -w "%{http_code}" "${BASE_URL}/actuator/health" || true)"
    if [[ "${code}" == "200" ]]; then
      log "Backend is healthy."
      return 0
    fi
    sleep 1
  done

  log "FAIL: backend never became healthy."
  exit 1
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
  printf '[PASS] %-30s %s %s -> %s\n' "${label}" "${method}" "${path}" "${code}"
}

record_fail() {
  local label="$1"
  local method="$2"
  local path="$3"
  local expected="$4"
  local code="$5"
  local body_preview="$6"
  FAIL_COUNT=$((FAIL_COUNT + 1))
  printf '[FAIL] %-30s %s %s -> %s (expected %s)\n' "${label}" "${method}" "${path}" "${code}" "${expected}"
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
  local body_must_contain="${6:-}"
  local body_preview

  http_request "${method}" "${path}" "${body}"
  body_preview="$(printf '%s' "${RESPONSE_BODY}" | tr '\n' ' ' | sed 's/[[:space:]]\+/ /g' | cut -c1-200)"

  if [[ "${RESPONSE_CODE}" =~ ^5[0-9][0-9]$ ]]; then
    record_fail "${label}" "${method}" "${path}" "${expected_codes}" "${RESPONSE_CODE}" "${body_preview}"
    return 0
  fi

  if ! matches_expected_code "${expected_codes}" "${RESPONSE_CODE}"; then
    record_fail "${label}" "${method}" "${path}" "${expected_codes}" "${RESPONSE_CODE}" "${body_preview}"
    return 0
  fi

  if [[ -n "${body_must_contain}" ]] && [[ "${RESPONSE_BODY}" != *"${body_must_contain}"* ]]; then
    record_fail "${label}" "${method}" "${path}" "${expected_codes}" "${RESPONSE_CODE}" "${body_preview}"
    printf '       missing body fragment: %s\n' "${body_must_contain}"
    return 0
  fi

  record_pass "${label}" "${method}" "${path}" "${RESPONSE_CODE}"
}

seed_smoke_data() {
  log "Seeding deterministic smoke data in local Postgres."
  (
    cd "${ROOT_DIR}"
    docker compose exec -T postgres psql -U moltrank -d moltrank <<SQL
BEGIN;

DELETE FROM commitment WHERE pair_id = ${SEED_PAIR_ID};
DELETE FROM subscription WHERE market_id = ${SEED_MARKET_ID};
DELETE FROM golden_set_item WHERE source IN ('smoke-seed', 'smoke-api');
DELETE FROM pair WHERE id = ${SEED_PAIR_ID};
DELETE FROM curator WHERE wallet = '${SEED_CURATOR_WALLET}' AND market_id = ${SEED_MARKET_ID};
DELETE FROM post WHERE id IN (${SEED_POST_A_ID}, ${SEED_POST_B_ID});
DELETE FROM round WHERE id = ${SEED_ROUND_ID};
DELETE FROM market WHERE id = ${SEED_MARKET_ID};
DELETE FROM identity WHERE wallet = '${SEED_CURATOR_WALLET}' OR id = ${SEED_IDENTITY_ID};

INSERT INTO identity (id, wallet, x_account, verified, created_at, updated_at)
VALUES (${SEED_IDENTITY_ID}, '${SEED_CURATOR_WALLET}', 'smoke_curator', true, NOW(), NOW());

INSERT INTO market (
  id, name, submolt_id, subscription_revenue, subscribers, creation_bond, max_pairs, created_at, updated_at
) VALUES (
  ${SEED_MARKET_ID}, 'Smoke Test Market 910101', 'smoke-910101', 1000000, 2, 0, 10, NOW(), NOW()
);

INSERT INTO round (
  id, market_id, status, pairs, base_per_pair, premium_per_pair, started_at, commit_deadline, reveal_deadline, created_at, updated_at
) VALUES (
  ${SEED_ROUND_ID}, ${SEED_MARKET_ID}, 'OPEN', 1, 100, 50, NOW(), NOW() + INTERVAL '1 hour', NOW() + INTERVAL '2 hours', NOW(), NOW()
);

INSERT INTO post (
  id, moltbook_id, market_id, agent, content, elo, matchups, wins, created_at, updated_at
) VALUES
  (${SEED_POST_A_ID}, 'smoke-post-a-910301', ${SEED_MARKET_ID}, 'smoke-agent-alpha', 'smoke content alpha', 1510, 4, 3, NOW(), NOW()),
  (${SEED_POST_B_ID}, 'smoke-post-b-910302', ${SEED_MARKET_ID}, 'smoke-agent-beta', 'smoke content beta', 1490, 4, 1, NOW(), NOW());

INSERT INTO pair (
  id, round_id, post_a, post_b, winner, total_stake, reward, is_golden, is_audit, golden_answer, created_at, settled_at
) VALUES (
  ${SEED_PAIR_ID}, ${SEED_ROUND_ID}, ${SEED_POST_A_ID}, ${SEED_POST_B_ID}, NULL, 0, 0, false, false, NULL, NOW(), NULL
);

INSERT INTO curator (
  wallet, identity_id, market_id, earned, lost, curator_score, calibration_rate, audit_pass_rate, alignment_stability, fraud_flags, pairs_this_epoch, created_at, updated_at
) VALUES (
  '${SEED_CURATOR_WALLET}', ${SEED_IDENTITY_ID}, ${SEED_MARKET_ID}, 1000, 100, 0.8000, 0.9000, 0.9500, 0.8500, 0, 0, NOW(), NOW()
);

INSERT INTO golden_set_item (
  id, post_a, post_b, correct_answer, confidence, source, created_at
) VALUES (
  ${SEED_GOLDEN_SET_ID}, ${SEED_POST_A_ID}, ${SEED_POST_B_ID}, 'A', 0.95, 'smoke-seed', NOW()
);

COMMIT;
SQL
  )
}

run_empty_state_checks() {
  log "Phase 1/2: empty-state endpoint checks."
  assert_request "feed-empty" "GET" "/api/feed?marketId=1&type=realtime&limit=5" "200"
  assert_request "rounds-empty" "GET" "/api/rounds?marketId=1" "200"
  assert_request "round-detail-missing" "GET" "/api/rounds/999999" "404"
  assert_request "agent-missing" "GET" "/api/agents/${MISSING_AGENT}" "404"
  assert_request "golden-list-empty" "GET" "/api/golden-set" "200"
  assert_request "pair-next-missing" "GET" "/api/pairs/next?wallet=${MISSING_WALLET}&marketId=1" "404"
  assert_request "commit-missing-pair" "POST" "/api/pairs/999999/commit" "404" \
    '{"curatorWallet":"smoke-missing-wallet-910101","hash":"0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","stake":1000,"encryptedReveal":"encrypted"}'
  assert_request "pool-health" "GET" "/api/pool" "200"
  assert_request "curator-missing" "GET" "/api/curators/${MISSING_WALLET}?marketId=1" "404"
  assert_request "leaderboard-empty" "GET" "/api/leaderboard?marketId=1&limit=10" "200"
  assert_request "identity-link-create" "POST" "/api/identity/link" "201" \
    "{\"wallet\":\"${EMPTY_LINK_WALLET}\",\"xAccount\":\"smoke_empty\",\"verified\":false}"
  assert_request "subscribe-bad-market" "POST" "/api/subscribe" "400" \
    '{"readerWallet":"smoke-reader-wallet-missing","market":{"id":999999},"amount":1000,"type":"REALTIME"}'
}

run_seeded_happy_path_checks() {
  log "Phase 2/2: seeded happy-path endpoint checks."
  assert_request "feed-seeded" "GET" "/api/feed?marketId=${SEED_MARKET_ID}&type=realtime&limit=5" "200" "" "\"agent\":\"smoke-agent-alpha\""
  assert_request "rounds-seeded" "GET" "/api/rounds?marketId=${SEED_MARKET_ID}" "200" "" "\"id\":${SEED_ROUND_ID}"
  assert_request "round-detail-seeded" "GET" "/api/rounds/${SEED_ROUND_ID}" "200" "" "\"status\":\"OPEN\""
  assert_request "agent-seeded" "GET" "/api/agents/smoke-agent-alpha" "200" "" "\"agentId\":\"smoke-agent-alpha\""
  assert_request "pair-next-seeded" "GET" "/api/pairs/next?wallet=${SEED_CURATOR_WALLET}&marketId=${SEED_MARKET_ID}" "200" "" "\"id\":${SEED_PAIR_ID}"
  assert_request "commit-seeded" "POST" "/api/pairs/${SEED_PAIR_ID}/commit" "201" \
    "{\"curatorWallet\":\"${SEED_CURATOR_WALLET}\",\"hash\":\"0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\",\"stake\":1000,\"encryptedReveal\":\"smoke-encrypted\"}"
  assert_request "curator-seeded" "GET" "/api/curators/${SEED_CURATOR_WALLET}?marketId=${SEED_MARKET_ID}" "200" "" "\"wallet\":\"${SEED_CURATOR_WALLET}\""
  assert_request "leaderboard-seeded" "GET" "/api/leaderboard?marketId=${SEED_MARKET_ID}&limit=10" "200" "" "\"wallet\":\"${SEED_CURATOR_WALLET}\""
  assert_request "subscribe-seeded" "POST" "/api/subscribe" "201" \
    "{\"readerWallet\":\"${SEED_READER_WALLET}\",\"market\":{\"id\":${SEED_MARKET_ID}},\"amount\":5000,\"type\":\"REALTIME\",\"round\":{\"id\":${SEED_ROUND_ID}}}"
  assert_request "golden-list-seeded" "GET" "/api/golden-set" "200" "" "\"id\":${SEED_GOLDEN_SET_ID}"
  assert_request "golden-delete-seeded" "DELETE" "/api/golden-set/${SEED_GOLDEN_SET_ID}" "204"
  assert_request "golden-create-seeded" "POST" "/api/golden-set" "201" \
    "{\"postA\":{\"id\":${SEED_POST_A_ID}},\"postB\":{\"id\":${SEED_POST_B_ID}},\"correctAnswer\":\"A\",\"confidence\":0.91,\"source\":\"smoke-api\"}"
  assert_request "identity-link-update" "POST" "/api/identity/link" "200" \
    "{\"wallet\":\"${SEED_CURATOR_WALLET}\",\"xAccount\":\"smoke_curator_updated\",\"verified\":true}"
}

print_summary_and_exit() {
  printf '\nSmoke summary: %d passed, %d failed.\n' "${PASS_COUNT}" "${FAIL_COUNT}"
  if [[ "${FAIL_COUNT}" -gt 0 ]]; then
    exit 1
  fi
}

main() {
  wait_for_backend
  run_empty_state_checks
  seed_smoke_data
  run_seeded_happy_path_checks
  print_summary_and_exit
}

main "$@"
