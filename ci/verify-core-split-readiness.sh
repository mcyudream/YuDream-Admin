#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

run_step() {
  label=$1
  script_path=$2
  echo "[verify-core-split-readiness] running ${label}"
  sh "$script_path"
}

run_step "core/plugin decoupling" "ci/verify-core-plugin-decoupling.sh"
run_step "contract package entry validation" "ci/verify-contract-packages.sh"
run_step "contract package tarball validation" "ci/verify-contract-package-tarballs.sh"
run_step "contract publish pipeline validation" "ci/verify-core-contract-publish-pipeline.sh"
run_step "plugin repo template validation" "ci/verify-plugin-repo-template.sh"
run_step "docs independence validation" "ci/verify-doc-independence.sh"

if [ "${VERIFY_PUBLISHED_NPM_CONTRACTS:-}" = "true" ]; then
  run_step "published npm contracts validation" "ci/verify-published-npm-contracts.sh"
else
  echo "[verify-core-split-readiness] skipping published npm contract reinstall check (set VERIFY_PUBLISHED_NPM_CONTRACTS=true to enable)"
fi

if [ "${VERIFY_PLUGIN_SPI_REGISTRY:-}" = "true" ]; then
  run_step "published plugin spi registry validation" "ci/verify-plugin-spi-registry.sh"
else
  echo "[verify-core-split-readiness] skipping published plugin spi registry check (set VERIFY_PLUGIN_SPI_REGISTRY=true to enable)"
fi

echo "[verify-core-split-readiness] OK"
