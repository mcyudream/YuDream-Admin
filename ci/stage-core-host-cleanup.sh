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

{
  printf '%s\n' \
    yudream-frontend/pnpm-workspace.yaml \
    yudream-frontend/pnpm-lock.yaml \
    yudream-frontend/apps/core-arco-design-vue/package.json \
    yudream-frontend/apps/core-arco-design-vue/src/plugins/sdk/index.ts \
    yudream-frontend/apps/core-arco-design-vue/src/views/dashboard/DashboardRemotePluginCard.vue \
    yudream-frontend/apps/core-arco-design-vue/src/views/platform/plugin/runtime-page.vue \
    yudream-frontend/apps/core-arco-design-vue/vite.config.ts \
    yudream-frontend/apps/core-arco-design-vue/vite/plugins.ts
  git status --short -- yudream-frontend/packages yudream-plugins | \
    sed -n 's/^.. //p' | \
    grep -E '^(yudream-frontend/packages/plugin-|yudream-plugins/yudream-plugin-)' | \
    grep -v '^yudream-frontend/packages/plugin-sdk/' | \
    grep -v '^yudream-frontend/packages/plugin-sdk$' | \
    grep -v '^yudream-plugins/yudream-plugin-spi/' | \
    grep -v '^yudream-plugins/yudream-plugin-spi$' || true
} | awk 'NF && !seen[$0]++' > "$PATHSPEC_FILE"

PREVIEW=$(git add -A -n --pathspec-from-file="$PATHSPEC_FILE")

if [ -z "$PREVIEW" ]; then
  echo "[stage-core-host-cleanup] no matching host cleanup changes to stage"
  exit 0
fi

echo "[stage-core-host-cleanup] staging host workspace cleanup and migrated plugin removals"

if [ "$DRY_RUN" = "true" ]; then
  printf '%s\n' "$PREVIEW"
else
  git add -A --pathspec-from-file="$PATHSPEC_FILE"
fi
