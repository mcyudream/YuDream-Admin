#!/usr/bin/env sh
set -eu

fail() {
  echo "[verify-plugin-remote-release-evidence] $1" >&2
  exit 1
}

resolve_json_runtime() {
  if command -v python >/dev/null 2>&1; then
    echo "python"
    return 0
  fi

  if command -v node >/dev/null 2>&1; then
    echo "node"
    return 0
  fi

  fail "python or node is required to parse GitLab jobs JSON"
}

get_jobs_json() {
  if [ -n "${GITLAB_JOBS_JSON_FILE:-}" ]; then
    jobs_json_file=$GITLAB_JOBS_JSON_FILE
    case "$jobs_json_file" in
      [A-Za-z]:\\* | [A-Za-z]:/*)
        if command -v cygpath >/dev/null 2>&1; then
          jobs_json_file=$(cygpath -u "$jobs_json_file")
        fi
        ;;
    esac

    cat "$jobs_json_file"
    return 0
  fi

  [ -n "${GITLAB_API_BASE:-}" ] || fail "GITLAB_API_BASE is required unless GITLAB_JOBS_JSON_FILE is set"
  [ -n "${GITLAB_PROJECT_ID:-}" ] || fail "GITLAB_PROJECT_ID is required unless GITLAB_JOBS_JSON_FILE is set"
  [ -n "${GITLAB_PIPELINE_ID:-}" ] || fail "GITLAB_PIPELINE_ID is required unless GITLAB_JOBS_JSON_FILE is set"

  if [ -n "${GITLAB_PRIVATE_TOKEN:-}" ]; then
    AUTH_HEADER="PRIVATE-TOKEN: ${GITLAB_PRIVATE_TOKEN}"
  elif [ -n "${GITLAB_JOB_TOKEN:-}" ]; then
    AUTH_HEADER="JOB-TOKEN: ${GITLAB_JOB_TOKEN}"
  else
    fail "GITLAB_PRIVATE_TOKEN or GITLAB_JOB_TOKEN is required unless GITLAB_JOBS_JSON_FILE is set"
  fi

  curl --fail --silent --show-error \
    --header "$AUTH_HEADER" \
    "${GITLAB_API_BASE%/}/projects/${GITLAB_PROJECT_ID}/pipelines/${GITLAB_PIPELINE_ID}/jobs"
}

require_job_status() {
  job_name=$1
  expected_status=$2

  printf '%s\n' "$JOBS_INDEX" | grep -F "$(printf '%s\t%s' "$job_name" "$expected_status")" >/dev/null 2>&1 && return 0

  echo "[verify-plugin-remote-release-evidence] available jobs:" >&2
  printf '%s\n' "$JOBS_INDEX" | sed 's/^/  - /' >&2
  fail "expected job '$job_name' to have status '$expected_status'"
}

JSON_RUNTIME=$(resolve_json_runtime)

if [ "$JSON_RUNTIME" = "python" ]; then
  JOBS_INDEX=$(get_jobs_json | python -c 'import json, sys
payload = sys.stdin.buffer.read().decode("utf-8-sig")
data = json.loads(payload)
for job in data:
    name = str(job.get("name", ""))
    status = str(job.get("status", ""))
    if name:
        print(f"{name}\t{status}")')
else
  JOBS_INDEX=$(get_jobs_json | node -e 'const fs = require("fs");
const raw = fs.readFileSync(0, "utf8").replace(/^\uFEFF/, "");
const data = JSON.parse(raw);
for (const job of data) {
  if (job && job.name) {
    console.log(`${job.name}\t${job.status ?? ""}`);
  }
}')
fi

require_job_status "validate:core-maven-registry" "success"
require_job_status "validate:core-npm-contracts" "success"
require_job_status "package:plugins" "success"
require_job_status "publish:plugin-jars" "success"
require_job_status "verify:published-plugin-jars" "success"

echo "[verify-plugin-remote-release-evidence] OK"
