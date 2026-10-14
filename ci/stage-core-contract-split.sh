#!/usr/bin/env sh
set -eu

usage() {
  echo "usage: $(basename "$0") [--dry-run]" >&2
  exit 1
}

DRY_RUN=false
if [ "${1:-}" = "--dry-run" ]; then
  DRY_RUN=true
  shift
fi
[ $# -eq 0 ] || usage

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

if ! git status --short -- \
  .gitignore \
  .gitlab-ci.yml \
  docs/dev/contract-release.md \
  docs/dev/backend-architecture.md \
  docs/dev/plugin-system.md \
  docs/dev/repo-sync.md \
  README.md \
  ci/verify-contract-packages.sh \
  ci/verify-contract-package-tarballs.sh \
  ci/verify-core-contract-publish-pipeline.sh \
  ci/verify-core-remote-release-evidence.sh \
  ci/verify-plugin-repo-readiness.sh \
  ci/stage-core-contract-split.sh \
  ci/show-core-split-status.sh \
  docs/plugin-system \
  docs/repository-split \
  pom.xml \
  templates/plugin-repo \
  yudream-plugins/yudream-plugin-spi/pom.xml \
  yudream-frontend/packages/plugin-sdk \
  yudream-frontend/packages/components/package.json | grep -q .; then
  echo "[stage-core-contract-split] no matching split changes to stage"
  exit 0
fi

echo "[stage-core-contract-split] staging contract, CI, template, and documentation paths"

if [ "$DRY_RUN" = "true" ]; then
  git add -A -n -- \
    .gitignore \
    .gitlab-ci.yml \
    docs/dev/contract-release.md \
    docs/dev/backend-architecture.md \
    docs/dev/plugin-system.md \
    docs/dev/repo-sync.md \
    README.md \
    ci/verify-contract-packages.sh \
    ci/verify-contract-package-tarballs.sh \
    ci/verify-core-contract-publish-pipeline.sh \
    ci/verify-core-remote-release-evidence.sh \
    ci/stage-core-contract-split.sh \
    ci/show-core-split-status.sh \
    docs/plugin-system \
    docs/repository-split \
    pom.xml \
    templates/plugin-repo \
    yudream-plugins/yudream-plugin-spi/pom.xml \
    yudream-frontend/packages/plugin-sdk \
    yudream-frontend/packages/components/package.json
else
  git add -A -- \
    .gitignore \
    .gitlab-ci.yml \
    docs/dev/contract-release.md \
    docs/dev/backend-architecture.md \
    docs/dev/plugin-system.md \
    docs/dev/repo-sync.md \
    README.md \
    ci/verify-contract-packages.sh \
    ci/verify-contract-package-tarballs.sh \
    ci/verify-core-contract-publish-pipeline.sh \
    ci/verify-core-remote-release-evidence.sh \
    ci/stage-core-contract-split.sh \
    ci/show-core-split-status.sh \
    docs/plugin-system \
    docs/repository-split \
    pom.xml \
    templates/plugin-repo \
    yudream-plugins/yudream-plugin-spi/pom.xml \
    yudream-frontend/packages/plugin-sdk \
    yudream-frontend/packages/components/package.json
fi
