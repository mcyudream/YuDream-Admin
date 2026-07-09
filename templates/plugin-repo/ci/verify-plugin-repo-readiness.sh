#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

run_step() {
  label=$1
  script_path=$2
  echo "[verify-plugin-repo-readiness] running ${label}"
  sh "$script_path"
}

has_plugin_jars() {
  find yudream-plugins -path '*/target/*.jar' -type f 2>/dev/null | grep -q .
}

run_step "plugin repo independence validation" "ci/verify-plugin-repo-independence.sh"
run_step "plugin maven boundary validation" "ci/verify-plugin-maven-boundary.sh"
run_step "plugin publish pipeline validation" "ci/verify-plugin-publish-pipeline.sh"
run_step "core npm contract reinstall validation" "ci/verify-core-npm-contracts.sh"
run_step "docs independence validation" "ci/verify-doc-independence.sh"

if [ -n "${CORE_PACKAGE_TOKEN:-}" ] || [ -n "${CI_JOB_TOKEN:-}" ]; then
  run_step "core maven registry validation" "ci/verify-core-maven-registry.sh"
else
  echo "[verify-plugin-repo-readiness] skipping core maven registry check (set CORE_PACKAGE_TOKEN or CI_JOB_TOKEN to enable)"
fi

if has_plugin_jars; then
  run_step "plugin jar asset validation" "ci/verify-plugin-jar-assets.sh"
else
  echo "[verify-plugin-repo-readiness] skipping plugin jar asset validation (build jars first with mvn clean package -DskipTests)"
fi

if [ "${VERIFY_PUBLISHED_PLUGIN_JARS:-}" = "true" ]; then
  run_step "published plugin jar validation" "ci/verify-published-plugin-jars.sh"
else
  echo "[verify-plugin-repo-readiness] skipping published plugin jar re-read check (set VERIFY_PUBLISHED_PLUGIN_JARS=true to enable)"
fi

echo "[verify-plugin-repo-readiness] OK"
