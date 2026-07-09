#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

fail() {
  echo "[verify-plugin-repo-independence] $1" >&2
  exit 1
}

search_tree() {
  pattern=$1
  shift
  if command -v rg >/dev/null 2>&1; then
    rg -n --no-messages "$pattern" "$@" >/dev/null 2>&1
  else
    grep -R -n -- "$pattern" "$@" >/dev/null 2>&1
  fi
}

echo "[verify-plugin-repo-independence] checking Maven parents"
if search_tree '<artifactId>YudreamAdmin</artifactId>' yudream-plugins; then
  fail "plugin repo modules must not inherit the core repository parent"
fi

if [ -d "yudream-plugins/yudream-plugin-spi" ]; then
  fail "plugin repo must not vendor core yudream-plugin-spi source"
fi

if grep -q '<module>yudream-plugins/yudream-plugin-spi</module>' pom.xml; then
  fail "plugin repo root pom must not include yudream-plugin-spi as a local module"
fi

echo "[verify-plugin-repo-independence] checking frontend package dependencies"
if search_tree '"@yudream/plugin-sdk":[[:space:]]*"workspace:\*"' yudream-frontend/packages; then
  fail "frontend plugin packages must depend on published @yudream/plugin-sdk versions"
fi

if search_tree '"@yudream/components":[[:space:]]*"workspace:\*"' yudream-frontend/packages; then
  fail "frontend plugin packages must depend on published @yudream/components versions"
fi

if grep -Eq '^[[:space:]]*-[[:space:]]+packages/\*$' yudream-frontend/pnpm-workspace.yaml; then
  fail "plugin repo frontend workspace must not use packages/*; keep it limited to packages/plugin-*"
fi

if ! grep -Eq '^[[:space:]]*-[[:space:]]+packages/plugin-\*$' yudream-frontend/pnpm-workspace.yaml; then
  fail "plugin repo frontend workspace must explicitly include packages/plugin-*"
fi

echo "[verify-plugin-repo-independence] checking CI entry boundaries"
if grep -Eq '^[[:space:]]*-[[:space:]]+yudream-frontend/packages/\*/package\.json$' .gitlab-ci.yml; then
  fail "plugin repo CI must not use yudream-frontend/packages/*/package.json; keep it limited to plugin packages"
fi

if ! grep -Eq '^[[:space:]]*-[[:space:]]+yudream-frontend/packages/plugin-\*/package\.json$' .gitlab-ci.yml; then
  fail "plugin repo CI must explicitly match yudream-frontend/packages/plugin-*/package.json"
fi

if ! grep -q 'pnpm -r --filter=@yudream/plugin-\* run build' .gitlab-ci.yml; then
  fail "plugin repo CI frontend build must explicitly filter @yudream/plugin-* packages"
fi

if ! grep -Eq '^[[:space:]]*-[[:space:]]+yudream-frontend/packages/plugin-\*/dist/$' .gitlab-ci.yml; then
  fail "plugin repo CI artifacts must stay limited to yudream-frontend/packages/plugin-*/dist/"
fi

if [ -d "yudream-frontend/packages/plugin-sdk" ]; then
  fail "plugin repo must not vendor core @yudream/plugin-sdk source"
fi

if [ -d "yudream-frontend/packages/components" ]; then
  fail "plugin repo must not vendor core @yudream/components source"
fi

if [ -f "yudream-frontend/pnpm-lock.yaml" ]; then
  if grep -q 'link:\.\./\.\./packages/' yudream-frontend/pnpm-lock.yaml; then
    fail "plugin repo pnpm lockfile must not link local workspace packages from core/shared source"
  fi
  if grep -q '^  packages/plugin-sdk:' yudream-frontend/pnpm-lock.yaml; then
    fail "plugin repo pnpm lockfile must not contain a local packages/plugin-sdk importer"
  fi
  if grep -q '^  packages/components:' yudream-frontend/pnpm-lock.yaml; then
    fail "plugin repo pnpm lockfile must not contain a local packages/components importer"
  fi
fi

echo "[verify-plugin-repo-independence] checking host-coupled source references"
if search_tree 'core-arco-design-vue/src/views' yudream-frontend/packages yudream-plugins; then
  fail "plugin repo must not import host app view source"
fi

if search_tree 'packages/plugin-.*/src/index.ts' yudream-frontend/packages yudream-plugins; then
  fail "plugin repo must not rely on core workspace plugin source paths"
fi

if [ -d "yudream-frontend/shared" ]; then
  fail "plugin repo must not keep local frontend shared helper shims after published SDK helper is available"
fi

if search_tree '\.\./\.\./shared/vite-shared' yudream-frontend/packages; then
  fail "plugin repo vite configs must not fall back to local shared vite helper"
fi

if search_tree '(packages/plugin-sdk|packages/components|plugin-sdk/src/index|components/src/index|host-components)' yudream-frontend/packages yudream-plugins; then
  fail "plugin repo source/config must not reference local core shared package paths or host-components internals"
fi

echo "[verify-plugin-repo-independence] checking shared package public API boundaries"
if command -v rg >/dev/null 2>&1; then
  invalid_shared_imports=$(rg -n --no-messages '@yudream/(plugin-sdk|components)/' yudream-frontend/packages yudream-plugins -g '!**/node_modules/**' | grep -v '@yudream/plugin-sdk/vite-shared' | grep -v '@yudream/components/resolver' || true)
else
  invalid_shared_imports=$(grep -R -n '@yudream/\(plugin-sdk\|components\)/' yudream-frontend/packages yudream-plugins 2>/dev/null | grep -v '@yudream/plugin-sdk/vite-shared' | grep -v '@yudream/components/resolver' || true)
fi

if [ -n "$invalid_shared_imports" ]; then
  echo "$invalid_shared_imports" >&2
  fail "plugin repo must use only published shared package public subpath APIs"
fi

if command -v rg >/dev/null 2>&1; then
  total_vite_configs=$(find yudream-frontend/packages -name 'vite.config.ts' | wc -l | tr -d ' ')
  matched_vite_configs=$(rg -l --no-messages '@yudream/plugin-sdk/vite-shared' yudream-frontend/packages -g 'vite.config.ts' | wc -l | tr -d ' ')
  if [ "$matched_vite_configs" -ne "$total_vite_configs" ]; then
    fail "every plugin vite config must use published @yudream/plugin-sdk/vite-shared helper"
  fi
else
  for file in yudream-frontend/packages/plugin-*/vite.config.ts; do
    if [ -f "$file" ] && ! grep -q '@yudream/plugin-sdk/vite-shared' "$file"; then
      fail "every plugin vite config must use published @yudream/plugin-sdk/vite-shared helper"
    fi
  done
fi

echo "[verify-plugin-repo-independence] OK"
