#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

fail() {
  echo "[verify-plugin-ui-conformance] $1" >&2
  exit 1
}

command -v pnpm >/dev/null 2>&1 || fail "pnpm is required"
[ -f yudream-frontend/package.json ] || fail "yudream-frontend/package.json is required"
[ -f yudream-frontend/pnpm-workspace.yaml ] || fail "copy pnpm-workspace.yaml.example to yudream-frontend/pnpm-workspace.yaml"

pnpm --dir yudream-frontend exec node -e "require.resolve('vue/compiler-sfc'); require.resolve('@yudream/components/resolver')" \
  || fail "installed Vue compiler and published YD components resolver are required"
pnpm --dir yudream-frontend run test:ui-audit
pnpm --dir yudream-frontend run audit:ui

echo "[verify-plugin-ui-conformance] OK"
