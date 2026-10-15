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
require_file "yudream-frontend/packages/plugin-sdk/uno.config.js"
require_file "yudream-frontend/packages/plugin-sdk/uno.config.d.ts"
require_file "yudream-frontend/packages/plugin-sdk/src/uno-config.ts"

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

if ! grep -q '"./uno-config"' yudream-frontend/packages/plugin-sdk/package.json; then
  fail "@yudream/plugin-sdk must export the ./uno-config subpath"
fi

if ! grep -q "yuDreamPluginUnoCss" yudream-frontend/packages/plugin-sdk/uno.config.js; then
  fail "uno.config.js must expose the yuDreamPluginUnoCss vite plugin factory"
fi

if ! grep -q "yuDreamPluginUnoCss" yudream-frontend/packages/plugin-sdk/uno.config.d.ts; then
  fail "uno.config.d.ts must declare the yuDreamPluginUnoCss signature"
fi

if ! grep -q '"unocss"' yudream-frontend/packages/plugin-sdk/package.json; then
  fail "@yudream/plugin-sdk must carry unocss as a published dependency for ./uno-config"
fi

echo "[verify-contract-packages] checking npm publish registries"
grep -Eq '"version":[[:space:]]*"[0-9]+\.[0-9]+\.[0-9]+"' yudream-frontend/packages/plugin-sdk/package.json \
  || fail "@yudream/plugin-sdk must use a stable version from its package.json"
grep -Eq '"version":[[:space:]]*"[0-9]+\.[0-9]+\.[0-9]+"' yudream-frontend/packages/components/package.json \
  || fail "@yudream/components must use a stable version from its package.json"
SDK_VERSION=$(sed -n 's/^[[:space:]]*"version":[[:space:]]*"\([^"]*\)".*/\1/p' yudream-frontend/packages/plugin-sdk/package.json | head -n 1)
grep -Fq "YUDREAM_PLUGIN_SDK_VERSION = '$SDK_VERSION'" yudream-frontend/apps/core-arco-design-vue/src/plugins/sdk/index.ts \
  || fail "host SDK runtime version must match the published SDK package version"
if ! grep -q '"registry":[[:space:]]*"https://nexus.yudream.online/repository/npm-public/"' yudream-frontend/packages/plugin-sdk/package.json; then
  fail "@yudream/plugin-sdk must publish to Nexus npm-public"
fi

if ! grep -q '"registry":[[:space:]]*"https://nexus.yudream.online/repository/npm-public/"' yudream-frontend/packages/components/package.json; then
  fail "@yudream/components must publish to Nexus npm-public"
fi

echo "[verify-contract-packages] checking component ledgers for duplicate entries"
BRIDGE_DUPES=$(grep -oE 'export const [A-Za-z0-9_]+' yudream-frontend/packages/plugin-sdk/src/host-components.ts | sort | uniq -d | tr '\n' ' ')
[ -z "$BRIDGE_DUPES" ] || fail "plugin-sdk host-components.ts must not declare duplicate exports (breaks SDK tsc build): $BRIDGE_DUPES"
RESOLVER_DUPES=$(grep -oE "^[[:space:]]*'[A-Za-z0-9]+'," yudream-frontend/packages/components/resolver.ts | sort | uniq -d | tr '\n' ' ')
[ -z "$RESOLVER_DUPES" ] || fail "components resolver must not repeat component names: $RESOLVER_DUPES"

echo "[verify-contract-packages] checking @yudream/dataviz"
grep -q '"version":[[:space:]]*"0.1.2"' yudream-frontend/packages/dataviz/package.json \
  || fail "@yudream/dataviz must use stable version 0.1.2"
grep -q '"./src/index.ts"' yudream-frontend/packages/dataviz/package.json \
  || fail "@yudream/dataviz must export the published src entry"
grep -q '"./echarts"' yudream-frontend/packages/dataviz/package.json \
  || fail "@yudream/dataviz must export the echarts-only subpath"
if ! grep -q '"registry":[[:space:]]*"https://nexus.yudream.online/repository/npm-public/"' yudream-frontend/packages/dataviz/package.json; then
  fail "@yudream/dataviz must publish to Nexus npm-public"
fi
grep -q '"echarts"' yudream-frontend/packages/dataviz/package.json \
  || fail "@yudream/dataviz must carry echarts as a published dependency"
grep -q '"d3"' yudream-frontend/packages/dataviz/package.json \
  || fail "@yudream/dataviz must carry d3 as a published dependency"

echo "[verify-contract-packages] OK"
