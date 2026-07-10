#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

fail() {
  echo "[verify-contract-packages] $1" >&2
  exit 1
}

require_file() {
  file=$1
  if [ ! -f "$file" ]; then
    fail "missing required contract package file: $file"
  fi
}

echo "[verify-contract-packages] checking published SDK entry files"
require_file "yudream-frontend/packages/plugin-sdk/scripts/sync-vite-shared.mjs"
require_file "yudream-frontend/packages/plugin-sdk/vite-shared.js"
require_file "yudream-frontend/packages/plugin-sdk/vite-shared.d.ts"

if ! grep -q '"sync:vite-shared":[[:space:]]*"node \./scripts/sync-vite-shared.mjs"' yudream-frontend/packages/plugin-sdk/package.json; then
  fail "@yudream/plugin-sdk must generate vite-shared entry files deterministically"
fi

if ! grep -q '"prepack":[[:space:]]*"pnpm run sync:vite-shared"' yudream-frontend/packages/plugin-sdk/package.json; then
  fail "@yudream/plugin-sdk must refresh vite-shared entry files before publish"
fi

if ! grep -q "./src/host-vue.ts" yudream-frontend/packages/plugin-sdk/vite-shared.js; then
  fail "vite-shared.js must point at published src/host-vue.ts"
fi

if ! grep -q "'vue-router': string" yudream-frontend/packages/plugin-sdk/vite-shared.d.ts; then
  fail "vite-shared.d.ts must expose the published alias signature"
fi

echo "[verify-contract-packages] checking npm publish registries"
grep -q '"version":[[:space:]]*"1.0.1"' yudream-frontend/packages/plugin-sdk/package.json \
  || fail "@yudream/plugin-sdk must use stable version 1.0.1"
grep -q '"version":[[:space:]]*"1.0.0"' yudream-frontend/packages/components/package.json \
  || fail "@yudream/components must use stable version 1.0.0"
if ! grep -q '"registry":[[:space:]]*"https://nexus.yudream.online/repository/npm-public/"' yudream-frontend/packages/plugin-sdk/package.json; then
  fail "@yudream/plugin-sdk must publish to Nexus npm-public"
fi

if ! grep -q '"registry":[[:space:]]*"https://nexus.yudream.online/repository/npm-public/"' yudream-frontend/packages/components/package.json; then
  fail "@yudream/components must publish to Nexus npm-public"
fi

echo "[verify-contract-packages] OK"
