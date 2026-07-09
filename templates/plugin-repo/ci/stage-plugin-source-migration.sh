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

PATHSPEC_FILE=$(mktemp)
cleanup() {
  rm -f "$PATHSPEC_FILE"
}
trap cleanup EXIT INT TERM

git status --short -- yudream-frontend/packages yudream-plugins | \
  sed -n 's/^.. //p' | \
  grep -E '^(yudream-frontend/packages/plugin-|yudream-plugins/yudream-plugin-)' | \
  awk 'NF && !seen[$0]++' > "$PATHSPEC_FILE" || true

PREVIEW=$(git add -A -n --pathspec-from-file="$PATHSPEC_FILE")

if [ -z "$PREVIEW" ]; then
  echo "[stage-plugin-source-migration] no matching migrated plugin source changes to stage"
  exit 0
fi

echo "[stage-plugin-source-migration] staging migrated plugin frontend and backend sources"

if [ "$DRY_RUN" = "true" ]; then
  printf '%s\n' "$PREVIEW"
else
  git add -A --pathspec-from-file="$PATHSPEC_FILE"
fi
