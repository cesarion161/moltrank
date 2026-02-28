#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_PORT="${BACKEND_PORT:-18080}"
BASE_URL="${BASE_URL:-http://localhost:${BACKEND_PORT}}"
DB_PASSWORD="${DB_PASSWORD:-changeme}"
WAIT_SECONDS="${WAIT_SECONDS:-180}"
TOURNAMENT_TIMEOUT_SECONDS="${TOURNAMENT_TIMEOUT_SECONDS:-240}"
POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS:-2}"
START_DELAY_SECONDS="${START_DELAY_SECONDS:-30}"
ENTRY_CLOSE_DELAY_SECONDS="${ENTRY_CLOSE_DELAY_SECONDS:-15}"
AUTO_START_DB="${AUTO_START_DB:-true}"
AUTO_START_BACKEND="${AUTO_START_BACKEND:-true}"
KEEP_BACKEND_RUNNING="${KEEP_BACKEND_RUNNING:-false}"

RUN_ID="${RUN_ID:-demo-$(date -u +%Y%m%dT%H%M%SZ)}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ROOT_DIR}/artifacts/clawgic-demo/${RUN_ID}}"
BACKEND_LOG_FILE="${ARTIFACT_DIR}/backend.log"

BACKEND_PID=""
BACKEND_STARTED=false

log() {
  printf '[clawgic-demo] %s\n' "$*"
}

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    printf 'Missing required command: %s\n' "${cmd}" >&2
    exit 1
  fi
}

cleanup() {
  local code=$?
  trap - EXIT INT TERM

  if [[ "${BACKEND_STARTED}" == "true" && -n "${BACKEND_PID}" && "${KEEP_BACKEND_RUNNING}" != "true" ]]; then
    kill "${BACKEND_PID}" 2>/dev/null || true
    wait "${BACKEND_PID}" 2>/dev/null || true
  fi

  if [[ "${code}" -ne 0 ]]; then
    printf '\n[clawgic-demo] failed (exit code %s)\n' "${code}" >&2
    printf '[clawgic-demo] artifacts: %s\n' "${ARTIFACT_DIR}" >&2
    if [[ -f "${BACKEND_LOG_FILE}" ]]; then
      printf '[clawgic-demo] backend log: %s\n' "${BACKEND_LOG_FILE}" >&2
    fi
  fi

  exit "${code}"
}

trap cleanup EXIT INT TERM

ensure_integer() {
  local label="$1"
  local value="$2"
  if [[ ! "${value}" =~ ^[0-9]+$ ]]; then
    printf '%s must be a non-negative integer: %s\n' "${label}" "${value}" >&2
    exit 1
  fi
}

api_request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local response_file="$4"

  if [[ -n "${body}" ]]; then
    curl -sS -o "${response_file}" -w "%{http_code}" \
      -X "${method}" \
      -H "Content-Type: application/json" \
      --data "${body}" \
      "${BASE_URL}${path}" || true
  else
    curl -sS -o "${response_file}" -w "%{http_code}" \
      -X "${method}" \
      "${BASE_URL}${path}" || true
  fi
}

assert_status() {
  local label="$1"
  local code="$2"
  local expected_codes="$3"
  local response_file="$4"

  if [[ "${code}" =~ ^(${expected_codes})$ ]]; then
    return 0
  fi

  local body_preview
  body_preview="$(tr '\n' ' ' < "${response_file}" | sed 's/[[:space:]]\+/ /g' | cut -c1-240)"
  printf '[clawgic-demo] %s failed -> status=%s expected=%s body=%s\n' \
    "${label}" "${code}" "${expected_codes}" "${body_preview}" >&2
  exit 1
}

json_get() {
  local json_file="$1"
  local path="$2"

  python3 - "${json_file}" "${path}" <<'PY'
import json
import re
import sys

json_file = sys.argv[1]
path = sys.argv[2]

with open(json_file, "r", encoding="utf-8") as handle:
    current = json.load(handle)

for token in [segment for segment in path.split(".") if segment]:
    indexed = re.fullmatch(r"([A-Za-z0-9_-]+)\[(\d+)\]", token)
    if indexed:
        key = indexed.group(1)
        idx = int(indexed.group(2))
        current = current[key][idx]
        continue
    if re.fullmatch(r"\d+", token):
        current = current[int(token)]
        continue
    current = current[token]

if current is None:
    print("")
elif isinstance(current, bool):
    print("true" if current else "false")
else:
    print(current)
PY
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

wallet_for_index() {
  local index="$1"
  python3 - "${RUN_ID}" "${index}" <<'PY'
import hashlib
import sys

run_id = sys.argv[1]
index = sys.argv[2]
seed = f"{run_id}-{index}".encode("utf-8")
print("0x" + hashlib.sha256(seed).hexdigest()[:40])
PY
}

start_backend() {
  log "Starting backend on ${BASE_URL} (log: ${BACKEND_LOG_FILE})"
  (
    cd "${ROOT_DIR}/backend"
    DB_PASSWORD="${DB_PASSWORD}" ./gradlew --no-daemon bootRun \
      --args="--server.port=${BACKEND_PORT} --clawgic.enabled=true --clawgic.mock-provider=true --clawgic.mock-judge=true --clawgic.worker.enabled=true --clawgic.worker.queue-mode=in-memory --clawgic.worker.initial-delay-ms=1000 --clawgic.worker.poll-interval-ms=1000 --x402.enabled=false --x402.dev-bypass-enabled=true --clawgic.ingestion.enabled=false --clawgic.ingestion.run-on-startup=false" \
      >"${BACKEND_LOG_FILE}" 2>&1
  ) &
  BACKEND_PID=$!
  BACKEND_STARTED=true
}

wait_for_backend() {
  log "Waiting for ${BASE_URL}/actuator/health"
  local health_file="${ARTIFACT_DIR}/actuator-health.json"

  for _ in $(seq 1 "${WAIT_SECONDS}"); do
    if [[ "${BACKEND_STARTED}" == "true" && -n "${BACKEND_PID}" ]] && ! kill -0 "${BACKEND_PID}" 2>/dev/null; then
      printf 'Backend process exited before becoming healthy. See %s\n' "${BACKEND_LOG_FILE}" >&2
      exit 1
    fi

    local code
    code="$(api_request "GET" "/actuator/health" "" "${health_file}")"
    if [[ "${code}" == "200" ]]; then
      log "Backend is healthy."
      return 0
    fi
    sleep 1
  done

  printf 'Backend did not become healthy within %s seconds. See %s\n' \
    "${WAIT_SECONDS}" "${BACKEND_LOG_FILE}" >&2
  exit 1
}

create_agent() {
  local index="$1"
  local wallet="$2"
  local agent_file="${ARTIFACT_DIR}/agent-${index}.json"
  local payload
  payload="$(cat <<JSON
{"walletAddress":"${wallet}","name":"C52 Demo Agent ${index}","avatarUrl":"https://example.com/agent-${index}.png","systemPrompt":"Debate clearly with deterministic, testable claims.","skillsMarkdown":"- structured rebuttals\\n- concise evidence framing","persona":"A methodical systems debater focused on reproducibility.","agentsMdSource":"# Demo Agent ${index}\\n\\n- Objective: Win with clear argument structure.","providerType":"MOCK","providerKeyRef":"demo-provider-${index}","apiKey":"demo-mock-key-${index}"}
JSON
)"

  local code
  code="$(api_request "POST" "/api/clawgic/agents" "${payload}" "${agent_file}")"
  assert_status "create-agent-${index}" "${code}" "201" "${agent_file}"
}

create_tournament() {
  local start_time="$1"
  local entry_close_time="$2"
  local tournament_file="${ARTIFACT_DIR}/tournament-create.json"
  local payload
  payload="$(cat <<JSON
{"topic":"C52 demo ${RUN_ID}: Should deterministic mock providers remain the default for hackathon MVP validation?","startTime":"${start_time}","entryCloseTime":"${entry_close_time}","baseEntryFeeUsdc":5.0}
JSON
)"
  local code
  code="$(api_request "POST" "/api/clawgic/tournaments" "${payload}" "${tournament_file}")"
  assert_status "create-tournament" "${code}" "201" "${tournament_file}"
}

poll_tournament_until_complete() {
  local tournament_id="$1"
  local results_latest="${ARTIFACT_DIR}/results-latest.json"
  local results_final="${ARTIFACT_DIR}/results-final.json"
  local deadline_epoch
  deadline_epoch="$(( $(date +%s) + TOURNAMENT_TIMEOUT_SECONDS ))"
  local last_status=""

  while true; do
    local code
    code="$(api_request "GET" "/api/clawgic/tournaments/${tournament_id}/results" "" "${results_latest}")"
    assert_status "poll-results" "${code}" "200" "${results_latest}"

    local status
    status="$(json_get "${results_latest}" "tournament.status")"
    if [[ "${status}" != "${last_status}" ]]; then
      log "Tournament status -> ${status}"
      last_status="${status}"
    fi

    if [[ "${status}" == "COMPLETED" ]]; then
      cp "${results_latest}" "${results_final}"
      return 0
    fi

    if (( $(date +%s) >= deadline_epoch )); then
      printf 'Timed out waiting for tournament completion (%s seconds)\n' "${TOURNAMENT_TIMEOUT_SECONDS}" >&2
      exit 1
    fi
    sleep "${POLL_INTERVAL_SECONDS}"
  done
}

build_summary_files() {
  local seeded_agents_file="${ARTIFACT_DIR}/seeded-agents.tsv"
  local results_file="${ARTIFACT_DIR}/results-final.json"
  local winner_file="${ARTIFACT_DIR}/winner-agent.json"
  local leaderboard_file="${ARTIFACT_DIR}/leaderboard.json"
  local summary_json="${ARTIFACT_DIR}/summary.json"
  local summary_txt="${ARTIFACT_DIR}/summary.txt"

  RUN_ID="${RUN_ID}" BASE_URL="${BASE_URL}" python3 - \
    "${seeded_agents_file}" \
    "${results_file}" \
    "${winner_file}" \
    "${leaderboard_file}" \
    "${summary_json}" \
    "${summary_txt}" <<'PY'
from collections import Counter
from datetime import datetime, timezone
import json
import os
import sys

seeded_agents_path = sys.argv[1]
results_path = sys.argv[2]
winner_path = sys.argv[3]
leaderboard_path = sys.argv[4]
summary_json_path = sys.argv[5]
summary_txt_path = sys.argv[6]

agents = []
with open(seeded_agents_path, "r", encoding="utf-8") as handle:
    for line in handle:
        line = line.strip()
        if not line:
            continue
        agent_id, name, wallet = line.split("\t")
        agents.append({"agentId": agent_id, "name": name, "walletAddress": wallet})

with open(results_path, "r", encoding="utf-8") as handle:
    results = json.load(handle)
with open(winner_path, "r", encoding="utf-8") as handle:
    winner = json.load(handle)
with open(leaderboard_path, "r", encoding="utf-8") as handle:
    leaderboard = json.load(handle)

tournament = results["tournament"]
matches = results.get("matches", [])
entries = results.get("entries", [])
status_counts = Counter(match.get("status", "UNKNOWN") for match in matches)

summary = {
    "runId": os.environ["RUN_ID"],
    "generatedAtUtc": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
    "baseUrl": os.environ["BASE_URL"],
    "tournament": {
        "id": tournament.get("tournamentId"),
        "status": tournament.get("status"),
        "topic": tournament.get("topic"),
        "winnerAgentId": tournament.get("winnerAgentId"),
        "matchesCompleted": tournament.get("matchesCompleted"),
        "matchesForfeited": tournament.get("matchesForfeited"),
        "startedAt": tournament.get("startedAt"),
        "completedAt": tournament.get("completedAt"),
    },
    "winner": {
        "agentId": winner.get("agentId"),
        "name": winner.get("name"),
        "walletAddress": winner.get("walletAddress"),
        "currentElo": (winner.get("elo") or {}).get("currentElo"),
        "matchesPlayed": (winner.get("elo") or {}).get("matchesPlayed"),
        "matchesWon": (winner.get("elo") or {}).get("matchesWon"),
        "matchesForfeited": (winner.get("elo") or {}).get("matchesForfeited"),
    },
    "seededAgents": agents,
    "entryCount": len(entries),
    "matchCount": len(matches),
    "matchStatusCounts": dict(status_counts),
    "leaderboardTop3": leaderboard.get("entries", [])[:3],
}

with open(summary_json_path, "w", encoding="utf-8") as handle:
    json.dump(summary, handle, indent=2)
    handle.write("\n")

winner_name = summary["winner"]["name"] or summary["winner"]["agentId"] or "unknown"
winner_wallet = summary["winner"]["walletAddress"] or "unknown"
status_counts_str = ", ".join(
    f"{status}={count}" for status, count in sorted(summary["matchStatusCounts"].items())
) or "none"

text_lines = [
    f"Run ID: {summary['runId']}",
    f"Tournament ID: {summary['tournament']['id']}",
    f"Tournament Status: {summary['tournament']['status']}",
    f"Winner: {winner_name} ({winner_wallet})",
    f"Entries: {summary['entryCount']}",
    f"Matches: {summary['matchCount']}",
    f"Match Status Counts: {status_counts_str}",
    f"Completed At: {summary['tournament']['completedAt']}",
]

with open(summary_txt_path, "w", encoding="utf-8") as handle:
    handle.write("\n".join(text_lines) + "\n")
PY
}

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
  if [[ "${AUTO_START_DB}" == "true" ]]; then
    require_cmd docker
  fi

  mkdir -p "${ARTIFACT_DIR}"
  : > "${ARTIFACT_DIR}/seeded-agents.tsv"

  if [[ "${AUTO_START_DB}" == "true" ]]; then
    log "Starting postgres with docker compose"
    (cd "${ROOT_DIR}" && docker compose up -d postgres >/dev/null)
  fi

  if [[ "${AUTO_START_BACKEND}" == "true" ]]; then
    start_backend
  fi

  wait_for_backend

  log "Seeding deterministic demo agents"
  local agent_id
  local wallet
  local -a agent_ids=()
  for index in 1 2 3 4; do
    wallet="$(wallet_for_index "${index}")"
    create_agent "${index}" "${wallet}"
    agent_id="$(json_get "${ARTIFACT_DIR}/agent-${index}.json" "agentId")"
    printf '%s\tC52 Demo Agent %s\t%s\n' "${agent_id}" "${index}" "${wallet}" >> "${ARTIFACT_DIR}/seeded-agents.tsv"
    agent_ids+=("${agent_id}")
    log "Created agent ${index} (${agent_id})"
  done

  local start_time
  local entry_close_time
  start_time="$(iso_utc_plus_seconds "${START_DELAY_SECONDS}")"
  entry_close_time="$(iso_utc_plus_seconds "${ENTRY_CLOSE_DELAY_SECONDS}")"

  log "Creating tournament (start=${start_time}, entryClose=${entry_close_time})"
  create_tournament "${start_time}" "${entry_close_time}"
  local tournament_id
  tournament_id="$(json_get "${ARTIFACT_DIR}/tournament-create.json" "tournamentId")"
  log "Created tournament ${tournament_id}"

  log "Entering all seeded agents"
  local index=1
  for seeded_agent_id in "${agent_ids[@]}"; do
    local entry_payload
    entry_payload="{\"agentId\":\"${seeded_agent_id}\"}"
    local entry_file="${ARTIFACT_DIR}/entry-${index}.json"
    local code
    code="$(api_request "POST" "/api/clawgic/tournaments/${tournament_id}/enter" "${entry_payload}" "${entry_file}")"
    assert_status "enter-agent-${index}" "${code}" "201" "${entry_file}"
    index=$((index + 1))
  done

  log "Building MVP bracket"
  local bracket_file="${ARTIFACT_DIR}/bracket.json"
  local bracket_code
  bracket_code="$(api_request "POST" "/api/clawgic/tournaments/${tournament_id}/bracket" "" "${bracket_file}")"
  assert_status "create-bracket" "${bracket_code}" "201" "${bracket_file}"

  log "Waiting for worker to execute and judge all matches"
  poll_tournament_until_complete "${tournament_id}"

  local final_results="${ARTIFACT_DIR}/results-final.json"
  local winner_agent_id
  winner_agent_id="$(json_get "${final_results}" "tournament.winnerAgentId")"
  if [[ -z "${winner_agent_id}" ]]; then
    printf 'Tournament completed without winner_agent_id\n' >&2
    exit 1
  fi

  local winner_file="${ARTIFACT_DIR}/winner-agent.json"
  local winner_code
  winner_code="$(api_request "GET" "/api/clawgic/agents/${winner_agent_id}" "" "${winner_file}")"
  assert_status "winner-agent-detail" "${winner_code}" "200" "${winner_file}"

  local leaderboard_file="${ARTIFACT_DIR}/leaderboard.json"
  local leaderboard_code
  leaderboard_code="$(api_request "GET" "/api/clawgic/agents/leaderboard?offset=0&limit=10" "" "${leaderboard_file}")"
  assert_status "leaderboard" "${leaderboard_code}" "200" "${leaderboard_file}"

  build_summary_files

  local winner_name
  local winner_wallet
  winner_name="$(json_get "${winner_file}" "name")"
  winner_wallet="$(json_get "${winner_file}" "walletAddress")"

  log "Tournament completed successfully."
  log "Winner: ${winner_name} (${winner_wallet})"
  log "Artifacts written to: ${ARTIFACT_DIR}"
  printf '\n'
  cat "${ARTIFACT_DIR}/summary.txt"
}

main "$@"
