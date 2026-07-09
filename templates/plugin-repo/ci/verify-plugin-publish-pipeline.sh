#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

fail() {
  echo "[verify-plugin-publish-pipeline] $1" >&2
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

echo "[verify-plugin-publish-pipeline] checking required verification scripts"
require_file "ci/verify-plugin-repo-independence.sh"
require_file "ci/verify-plugin-maven-boundary.sh"
require_file "ci/verify-core-maven-registry.sh"
require_file "ci/verify-core-npm-contracts.sh"
require_file "ci/verify-plugin-jar-assets.sh"
require_file "ci/publish-plugin-jars.sh"
require_file "ci/verify-published-plugin-jars.sh"

echo "[verify-plugin-publish-pipeline] checking stage layout"
require_pattern '^[[:space:]]*-[[:space:]]\+validate$' "plugin CI must keep validate stage"
require_pattern '^[[:space:]]*-[[:space:]]\+build-frontend$' "plugin CI must keep build-frontend stage"
require_pattern '^[[:space:]]*-[[:space:]]\+package-plugin$' "plugin CI must keep package-plugin stage"
require_pattern '^[[:space:]]*-[[:space:]]\+publish-plugin$' "plugin CI must keep publish-plugin stage"
require_pattern '^[[:space:]]*-[[:space:]]\+verify-publish$' "plugin CI must keep verify-publish stage"

echo "[verify-plugin-publish-pipeline] checking validation jobs"
require_pattern '^validate:independence:$' "plugin CI must validate repository independence"
require_pattern '^validate:plugin-maven-boundary:$' "plugin CI must validate plugin Maven boundary"
require_pattern '^validate:core-maven-registry:$' "plugin CI must validate core Maven registry access"
require_pattern '^validate:core-npm-contracts:$' "plugin CI must validate core npm contracts"
require_pattern '^validate:docs:$' "plugin CI must validate documentation independence"
require_pattern '^validate:publish-pipeline:$' "plugin CI must validate its own publish pipeline shape"
require_pattern 'sh ci/verify-plugin-publish-pipeline.sh' "plugin CI must call ci/verify-plugin-publish-pipeline.sh"

echo "[verify-plugin-publish-pipeline] checking package/publish/verify chain"
require_pattern '^package:plugins:$' "plugin CI must keep package:plugins job"
require_pattern 'PACKAGE_MAVEN_REPO' "plugin CI package job must use a dedicated clean Maven local repository"
require_pattern 'sh ci/verify-plugin-jar-assets.sh' "plugin CI package job must verify plugin jar assets"
require_pattern '^publish:plugin-jars:$' "plugin CI must keep publish:plugin-jars job"
require_pattern 'sh ci/publish-plugin-jars.sh' "plugin CI publish job must upload plugin jars"
require_pattern '^verify:published-plugin-jars:$' "plugin CI must keep verify:published-plugin-jars job"
require_pattern 'sh ci/verify-published-plugin-jars.sh' "plugin CI must re-read published plugin jars after upload"

echo "[verify-plugin-publish-pipeline] checking publish rules"
require_pattern '\$CI_COMMIT_TAG =~ /\^v/' "plugin CI publish/verify jobs must stay tag-gated"

echo "[verify-plugin-publish-pipeline] OK"
