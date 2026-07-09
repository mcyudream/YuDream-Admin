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
  .gitlab-ci.yml \
  .npmrc.example \
  settings.xml.example \
  README.md \
  pom.xml \
  ci \
  docs \
  yudream-frontend/package.json \
  yudream-frontend/pnpm-workspace.yaml \
  yudream-frontend/pnpm-lock.yaml | grep -q .; then
  echo "[stage-plugin-repo-foundation] no matching repository foundation changes to stage"
  exit 0
fi

echo "[stage-plugin-repo-foundation] staging repository foundation, CI, and documentation paths"

if [ "$DRY_RUN" = "true" ]; then
  git add -A -n -- \
    .gitlab-ci.yml \
    .npmrc.example \
    settings.xml.example \
    README.md \
    pom.xml \
    ci \
    docs \
    yudream-frontend/package.json \
    yudream-frontend/pnpm-workspace.yaml \
    yudream-frontend/pnpm-lock.yaml
else
  git add -A -- \
    .gitlab-ci.yml \
    .npmrc.example \
    settings.xml.example \
    README.md \
    pom.xml \
    ci \
    docs \
    yudream-frontend/package.json \
    yudream-frontend/pnpm-workspace.yaml \
    yudream-frontend/pnpm-lock.yaml
fi
