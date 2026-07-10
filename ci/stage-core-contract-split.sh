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
  .npmrc.npmjs.example \
  .codex/skills/yudream-contract-release \
  .codex/skills/yudream-ddd-architecture/references/knowledge.json \
  README.md \
  ci \
  docs/plugin-system \
  docs/repository-split \
  pom.xml \
  skills/yudream-contract-release \
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
    .npmrc.npmjs.example \
    .codex/skills/yudream-contract-release \
    .codex/skills/yudream-ddd-architecture/references/knowledge.json \
    README.md \
    ci \
    docs/plugin-system \
    docs/repository-split \
    pom.xml \
    skills/yudream-contract-release \
    templates/plugin-repo \
    yudream-plugins/yudream-plugin-spi/pom.xml \
    yudream-frontend/packages/plugin-sdk \
    yudream-frontend/packages/components/package.json
else
  git add -A -- \
    .gitignore \
    .gitlab-ci.yml \
    .npmrc.npmjs.example \
    .codex/skills/yudream-contract-release \
    .codex/skills/yudream-ddd-architecture/references/knowledge.json \
    README.md \
    ci \
    docs/plugin-system \
    docs/repository-split \
    pom.xml \
    skills/yudream-contract-release \
    templates/plugin-repo \
    yudream-plugins/yudream-plugin-spi/pom.xml \
    yudream-frontend/packages/plugin-sdk \
    yudream-frontend/packages/components/package.json
fi
