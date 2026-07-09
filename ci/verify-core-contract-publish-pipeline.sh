#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

fail() {
  echo "[verify-core-contract-publish-pipeline] $1" >&2
  exit 1
}

require_file() {
  file=$1
  [ -f "$file" ] || fail "missing required file: $file"
}

require_pattern() {
  pattern=$1
  message=$2
  grep -q "$pattern" .gitlab-ci.yml || fail "$message"
}

echo "[verify-core-contract-publish-pipeline] checking required verification scripts"
require_file "ci/verify-plugin-spi-registry.sh"
require_file "ci/verify-published-npm-contracts.sh"

echo "[verify-core-contract-publish-pipeline] checking stage layout"
require_pattern '^[[:space:]]*-[[:space:]]\+publish-packages$' "core CI must keep publish-packages stage"
require_pattern '^[[:space:]]*-[[:space:]]\+verify-packages$' "core CI must keep verify-packages stage"

echo "[verify-core-contract-publish-pipeline] checking Maven publish/verify jobs"
require_pattern '^publish:maven-plugin-spi:$' "core CI must publish yudream-plugin-spi"
require_pattern '^verify:maven-plugin-spi:$' "core CI must verify published yudream-plugin-spi"
require_pattern 'sh ci/verify-plugin-spi-registry.sh' "core CI must call ci/verify-plugin-spi-registry.sh after Maven publish"

echo "[verify-core-contract-publish-pipeline] checking GitLab npm publish/verify jobs"
require_pattern '^publish:npm-plugin-sdk:$' "core CI must publish @yudream/plugin-sdk to GitLab npm"
require_pattern '^publish:npm-components:$' "core CI must publish @yudream/components to GitLab npm"
require_pattern '^verify:gitlab-npm-contracts:$' "core CI must verify GitLab npm published contracts"
require_pattern 'VERIFY_NPM_REGISTRY="\$CI_API_V4_URL/projects/\$CI_PROJECT_ID/packages/npm/" CI_JOB_TOKEN="\$CI_JOB_TOKEN" sh ci/verify-published-npm-contracts.sh' "core CI must re-install published GitLab npm contracts from project registry"

echo "[verify-core-contract-publish-pipeline] checking npmjs publish/verify jobs"
require_pattern '^publish:npmjs-plugin-sdk:$' "core CI must publish @yudream/plugin-sdk to npmjs"
require_pattern '^publish:npmjs-components:$' "core CI must publish @yudream/components to npmjs"
require_pattern '^verify:npmjs-contracts:$' "core CI must verify npmjs published contracts"
require_pattern 'VERIFY_NPM_REGISTRY="\$NPMJS_REGISTRY" VERIFY_NPM_TOKEN="\$NPM_TOKEN" sh ci/verify-published-npm-contracts.sh' "core CI must re-install published npmjs contracts from npmjs"

echo "[verify-core-contract-publish-pipeline] checking publish rules"
require_pattern '\$CI_COMMIT_TAG =~ /\^v/' "core CI publish/verify jobs must stay tag-gated"
require_pattern 'GITLAB_NPM_PUBLISH_ENABLED == "true"' "core CI GitLab npm publish chain must stay behind explicit flag"
require_pattern 'NPMJS_PUBLISH_ENABLED == "true"' "core CI npmjs publish chain must stay behind explicit flag"

echo "[verify-core-contract-publish-pipeline] OK"
