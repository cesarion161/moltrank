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
SEED_CURATOR_WALLET="9C6hybhQ6Aycep9jaUnP6uL9ZYvDjUp1aSkFWPUFJtpj"
SEED_SKIP_WALLET="smoke-skip-wallet-910102"
SEED_SKIP_IDENTITY_ID=910502
SEED_READER_WALLET="smoke-reader-wallet-910101"
MISSING_WALLET="smoke-missing-wallet-910101"
MISSING_AGENT="smoke-missing-agent-910101"
EMPTY_LINK_WALLET="smoke-empty-$(date +%s)"
BOOTSTRAP_MARKET_READER_WALLET="smoke-bootstrap-reader-910101"
SMOKE_SIGNING_SEED_HEX="0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20"
SMOKE_REQUEST_NONCE_HEX="00112233445566778899aabbccddeeff"
SMOKE_REVEAL_NONCE_HEX="00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"

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

db_scalar() {
  local sql="$1"
  (
    cd "${ROOT_DIR}"
    docker compose exec -T postgres psql -U moltrank -d moltrank -At -c "${sql}"
  ) | tr -d '[:space:]'
}

get_market_subscribers() {
  local market_id="$1"
  db_scalar "SELECT subscribers FROM market WHERE id = ${market_id};"
}

get_market_revenue() {
  local market_id="$1"
  db_scalar "SELECT subscription_revenue FROM market WHERE id = ${market_id};"
}

get_pool_balance() {
  db_scalar "SELECT balance FROM global_pool WHERE id = 1;"
}

assert_db_delta() {
  local label="$1"
  local before="$2"
  local after="$3"
  local expected_delta="$4"

  if ! [[ "${before}" =~ ^-?[0-9]+$ && "${after}" =~ ^-?[0-9]+$ ]]; then
    record_fail "${label}" "DB" "delta-check" "${expected_delta}" "invalid" "before=${before}, after=${after}"
    return 0
  fi

  local actual_delta
  actual_delta=$((after - before))
  if [[ "${actual_delta}" == "${expected_delta}" ]]; then
    record_pass "${label}" "DB" "delta-check" "${actual_delta}"
  else
    record_fail "${label}" "DB" "delta-check" "${expected_delta}" "${actual_delta}" "before=${before}, after=${after}"
  fi
}

build_signed_commit_payload() {
  local pair_id="$1"
  local stake_amount="$2"
  local choice="$3"

  source "${HOME}/.nvm/nvm.sh"
  (
    cd "${ROOT_DIR}/frontend"
    nvm use >/dev/null
    node --input-type=module - "${pair_id}" "${stake_amount}" "${choice}" "${SMOKE_REQUEST_NONCE_HEX}" "${SMOKE_REVEAL_NONCE_HEX}" "${SMOKE_SIGNING_SEED_HEX}" <<'NODE'
import crypto from 'node:crypto';
import { Keypair } from '@solana/web3.js';
import { ed25519 } from '@noble/curves/ed25519';
import { keccak_256 } from '@noble/hashes/sha3.js';
import { sha256 } from '@noble/hashes/sha2.js';

const [pairIdArg, stakeArg, choice, requestNonceHex, revealNonceHex, seedHex] = process.argv.slice(2);

const pairId = Number(pairIdArg);
const stakeAmount = Number(stakeArg);
const signedAt = Math.floor(Date.now() / 1000);

const seed = Buffer.from(seedHex, 'hex');
const keypair = Keypair.fromSeed(seed);
const wallet = keypair.publicKey.toBase58();

const choiceByte = choice === 'A' ? 0 : 1;
const revealNonce = Buffer.from(revealNonceHex, 'hex');
const pairIdBytes = Buffer.alloc(4);
pairIdBytes.writeUInt32LE(pairId);
const stakeBytes = Buffer.alloc(8);
stakeBytes.writeBigUInt64LE(BigInt(stakeAmount));

const preimage = Buffer.concat([
  Buffer.from(keypair.publicKey.toBytes()),
  pairIdBytes,
  Buffer.from([choiceByte]),
  stakeBytes,
  revealNonce,
]);
const commitmentHash = `0x${Buffer.from(keccak_256(preimage)).toString('hex')}`;

const authMessage = `moltrank-commit-v1|wallet=${wallet}|pairId=${pairId}|hash=${commitmentHash}|stake=${stakeAmount}|signedAt=${signedAt}|nonce=${requestNonceHex}`;
const authBytes = Buffer.from(authMessage, 'utf8');
const signature = Buffer.from(ed25519.sign(authBytes, seed));

const revealPayload = Buffer.concat([Buffer.from([choiceByte]), revealNonce]);
const revealKey = Buffer.from(sha256(signature));
const iv = Buffer.from('0102030405060708090a0b0c', 'hex');
const cipher = crypto.createCipheriv('aes-256-gcm', revealKey, iv, { authTagLength: 16 });
cipher.setAAD(authBytes);
const encryptedCore = Buffer.concat([cipher.update(revealPayload), cipher.final()]);
const encryptedReveal = Buffer.concat([encryptedCore, cipher.getAuthTag()]);

process.stdout.write(JSON.stringify({
  wallet,
  commitmentHash,
  stakeAmount,
  encryptedReveal: encryptedReveal.toString('base64'),
  revealIv: iv.toString('base64'),
  signature: signature.toString('base64'),
  signedAt,
  requestNonce: requestNonceHex,
}));
NODE
  )
}

seed_smoke_data() {
  log "Seeding deterministic smoke data in local Postgres."
  (
    cd "${ROOT_DIR}"
    docker compose exec -T postgres psql -U moltrank -d moltrank <<SQL
BEGIN;

DELETE FROM commitment WHERE pair_id = ${SEED_PAIR_ID};
DELETE FROM pair_skip WHERE pair_id = ${SEED_PAIR_ID};
DELETE FROM subscription WHERE market_id = ${SEED_MARKET_ID};
DELETE FROM subscription WHERE reader_wallet = '${BOOTSTRAP_MARKET_READER_WALLET}';
DELETE FROM golden_set_item WHERE source IN ('smoke-seed', 'smoke-api');
DELETE FROM pair WHERE id = ${SEED_PAIR_ID};
DELETE FROM curator WHERE wallet = '${SEED_CURATOR_WALLET}' AND market_id = ${SEED_MARKET_ID};
DELETE FROM post WHERE id IN (${SEED_POST_A_ID}, ${SEED_POST_B_ID});
DELETE FROM round WHERE id = ${SEED_ROUND_ID};
DELETE FROM market WHERE id = ${SEED_MARKET_ID};
DELETE FROM identity
WHERE wallet IN ('${SEED_CURATOR_WALLET}', '${SEED_SKIP_WALLET}')
   OR id IN (${SEED_IDENTITY_ID}, ${SEED_SKIP_IDENTITY_ID});

INSERT INTO identity (id, wallet, x_account, verified, created_at, updated_at)
VALUES (${SEED_IDENTITY_ID}, '${SEED_CURATOR_WALLET}', 'smoke_curator', true, NOW(), NOW());

INSERT INTO identity (id, wallet, x_account, verified, created_at, updated_at)
VALUES (${SEED_SKIP_IDENTITY_ID}, '${SEED_SKIP_WALLET}', 'smoke_skipper', true, NOW(), NOW());

INSERT INTO market (
  id, name, submolt_id, subscription_revenue, subscribers, creation_bond, max_pairs, created_at, updated_at
) VALUES (
  ${SEED_MARKET_ID}, 'Smoke Test Market 910101', 'smoke-910101', 1000000, 2, 0, 10, NOW(), NOW()
);

INSERT INTO round (
  id, market_id, status, pairs, base_per_pair, premium_per_pair, started_at, commit_deadline, reveal_deadline, created_at, updated_at
) VALUES (
  ${SEED_ROUND_ID}, ${SEED_MARKET_ID}, 'COMMIT', 1, 100, 50, NOW(), NOW() + INTERVAL '1 hour', NOW() + INTERVAL '2 hours', NOW(), NOW()
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

  local bootstrap_subscribers_before
  local bootstrap_revenue_before
  local bootstrap_pool_before
  bootstrap_subscribers_before="$(get_market_subscribers 1)"
  bootstrap_revenue_before="$(get_market_revenue 1)"
  bootstrap_pool_before="$(get_pool_balance)"

  assert_request "subscribe-bootstrap-market" "POST" "/api/subscribe" "201" \
    "{\"readerWallet\":\"${BOOTSTRAP_MARKET_READER_WALLET}\",\"market\":{\"id\":1},\"amount\":1000,\"type\":\"FREE_DELAY\"}" \
    "\"poolContribution\":0"

  local bootstrap_subscribers_after
  local bootstrap_revenue_after
  local bootstrap_pool_after
  bootstrap_subscribers_after="$(get_market_subscribers 1)"
  bootstrap_revenue_after="$(get_market_revenue 1)"
  bootstrap_pool_after="$(get_pool_balance)"

  assert_db_delta "subscribe-bootstrap-subscribers" "${bootstrap_subscribers_before}" "${bootstrap_subscribers_after}" 1
  assert_db_delta "subscribe-bootstrap-revenue" "${bootstrap_revenue_before}" "${bootstrap_revenue_after}" 0
  assert_db_delta "subscribe-bootstrap-pool" "${bootstrap_pool_before}" "${bootstrap_pool_after}" 0

  assert_request "subscribe-bad-market" "POST" "/api/subscribe" "400" \
    '{"readerWallet":"smoke-reader-wallet-missing","market":{"id":999999},"amount":1000,"type":"REALTIME"}'
}

run_seeded_happy_path_checks() {
  log "Phase 2/2: seeded happy-path endpoint checks."
  assert_request "feed-seeded" "GET" "/api/feed?marketId=${SEED_MARKET_ID}&type=realtime&limit=5" "200" "" "\"agent\":\"smoke-agent-alpha\""
  assert_request "rounds-seeded" "GET" "/api/rounds?marketId=${SEED_MARKET_ID}" "200" "" "\"id\":${SEED_ROUND_ID}"
  assert_request "round-detail-seeded" "GET" "/api/rounds/${SEED_ROUND_ID}" "200" "" "\"status\":\"COMMIT\""
  assert_request "agent-seeded" "GET" "/api/agents/smoke-agent-alpha" "200" "" "\"agentId\":\"smoke-agent-alpha\""
  assert_request "pair-next-seeded" "GET" "/api/pairs/next?wallet=${SEED_CURATOR_WALLET}&marketId=${SEED_MARKET_ID}" "200" "" "\"id\":${SEED_PAIR_ID}"
  assert_request "pair-next-skipper-before" "GET" "/api/pairs/next?wallet=${SEED_SKIP_WALLET}&marketId=${SEED_MARKET_ID}" "200" "" "\"id\":${SEED_PAIR_ID}"
  assert_request "pair-skip-seeded" "POST" "/api/pairs/${SEED_PAIR_ID}/skip" "204" \
    "{\"wallet\":\"${SEED_SKIP_WALLET}\"}"
  assert_request "pair-next-skipper-after" "GET" "/api/pairs/next?wallet=${SEED_SKIP_WALLET}&marketId=${SEED_MARKET_ID}" "404"

  local signed_commit_payload
  signed_commit_payload="$(build_signed_commit_payload "${SEED_PAIR_ID}" "1000" "A")"
  assert_request "commit-seeded" "POST" "/api/pairs/${SEED_PAIR_ID}/commit" "201" \
    "${signed_commit_payload}"
  assert_request "curator-seeded" "GET" "/api/curators/${SEED_CURATOR_WALLET}?marketId=${SEED_MARKET_ID}" "200" "" "\"wallet\":\"${SEED_CURATOR_WALLET}\""
  assert_request "leaderboard-seeded" "GET" "/api/leaderboard?marketId=${SEED_MARKET_ID}&limit=10" "200" "" "\"wallet\":\"${SEED_CURATOR_WALLET}\""

  local seeded_subscribers_before
  local seeded_revenue_before
  local seeded_pool_before
  seeded_subscribers_before="$(get_market_subscribers "${SEED_MARKET_ID}")"
  seeded_revenue_before="$(get_market_revenue "${SEED_MARKET_ID}")"
  seeded_pool_before="$(get_pool_balance)"

  assert_request "subscribe-seeded" "POST" "/api/subscribe" "201" \
    "{\"readerWallet\":\"${SEED_READER_WALLET}\",\"market\":{\"id\":${SEED_MARKET_ID}},\"amount\":5000,\"type\":\"REALTIME\",\"round\":{\"id\":${SEED_ROUND_ID}}}" \
    "\"poolContribution\":5000"

  local seeded_subscribers_after
  local seeded_revenue_after
  local seeded_pool_after
  seeded_subscribers_after="$(get_market_subscribers "${SEED_MARKET_ID}")"
  seeded_revenue_after="$(get_market_revenue "${SEED_MARKET_ID}")"
  seeded_pool_after="$(get_pool_balance)"

  assert_db_delta "subscribe-seeded-subscribers" "${seeded_subscribers_before}" "${seeded_subscribers_after}" 1
  assert_db_delta "subscribe-seeded-revenue" "${seeded_revenue_before}" "${seeded_revenue_after}" 5000
  assert_db_delta "subscribe-seeded-pool" "${seeded_pool_before}" "${seeded_pool_after}" 5000

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
