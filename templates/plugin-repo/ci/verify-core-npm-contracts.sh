#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

fail() {
  echo "[verify-core-npm-contracts] $1" >&2
  exit 1
}

if ! command -v pnpm >/dev/null 2>&1; then
  fail "pnpm is required"
fi

TARGET_REGISTRY="${CORE_NPM_REGISTRY:-}"
if [ -z "$TARGET_REGISTRY" ]; then
  TARGET_REGISTRY="${YUDREAM_NPM_REGISTRY:-https://registry.npmjs.org/}"
fi

if [ -n "${CORE_PACKAGE_TOKEN:-}" ]; then
  PACKAGE_TOKEN="$CORE_PACKAGE_TOKEN"
elif [ -n "${CI_JOB_TOKEN:-}" ]; then
  PACKAGE_TOKEN="$CI_JOB_TOKEN"
else
  PACKAGE_TOKEN=""
fi

SDK_VERSION=$(sed -n "s/^  '@yudream\\/plugin-sdk': //p" yudream-frontend/pnpm-workspace.yaml | head -n 1)
COMPONENTS_VERSION=$(sed -n "s/^  '@yudream\\/components': //p" yudream-frontend/pnpm-workspace.yaml | head -n 1)

[ -n "$SDK_VERSION" ] || fail "unable to resolve @yudream/plugin-sdk version from yudream-frontend/pnpm-workspace.yaml"
[ -n "$COMPONENTS_VERSION" ] || fail "unable to resolve @yudream/components version from yudream-frontend/pnpm-workspace.yaml"

WORK_ROOT="${CI_PROJECT_DIR:-$ROOT_DIR}"
VERIFY_DIR="${VERIFY_NPM_CONTRACTS_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/yudream-npm-contracts-XXXXXX")}"

trap 'rm -rf "$VERIFY_DIR"' EXIT INT TERM

mkdir -p "$VERIFY_DIR"

cat > "$VERIFY_DIR/package.json" <<EOF
{
  "name": "verify-yudream-core-npm-contracts",
  "private": true,
  "version": "0.0.0",
  "packageManager": "pnpm@11.9.0",
  "dependencies": {
    "@yudream/plugin-sdk": "${SDK_VERSION}",
    "@yudream/components": "${COMPONENTS_VERSION}",
    "vue": "^3.5.38",
    "vue-router": "^5.1.0"
  }
}
EOF

REGISTRY_HOST=$(printf '%s' "$TARGET_REGISTRY" | sed -E 's#^https?://##' | sed 's#/$##')
cat > "$VERIFY_DIR/.npmrc" <<EOF
registry=https://registry.npmjs.org/
@yudream:registry=${TARGET_REGISTRY}
strict-peer-dependencies=false
EOF

if [ -n "$PACKAGE_TOKEN" ] && [ "$TARGET_REGISTRY" != "https://registry.npmjs.org/" ] && [ "$TARGET_REGISTRY" != "http://registry.npmjs.org/" ]; then
  cat >> "$VERIFY_DIR/.npmrc" <<EOF
//${REGISTRY_HOST}/:_authToken=${PACKAGE_TOKEN}
always-auth=true
EOF
fi

echo "[verify-core-npm-contracts] installing @yudream/plugin-sdk@${SDK_VERSION} from ${TARGET_REGISTRY}"
echo "[verify-core-npm-contracts] installing @yudream/components@${COMPONENTS_VERSION} from ${TARGET_REGISTRY}"

pnpm --dir "$VERIFY_DIR" install --lockfile=false --ignore-scripts --config.strict-peer-dependencies=false >/dev/null

[ -f "$VERIFY_DIR/node_modules/@yudream/plugin-sdk/vite-shared.js" ] || fail "installed @yudream/plugin-sdk is missing vite-shared.js"
[ -f "$VERIFY_DIR/node_modules/@yudream/plugin-sdk/vite-shared.d.ts" ] || fail "installed @yudream/plugin-sdk is missing vite-shared.d.ts"
[ -f "$VERIFY_DIR/node_modules/@yudream/components/resolver.ts" ] || fail "installed @yudream/components is missing resolver.ts"

if grep -R -n -E '(workspace:|catalog:|link:|file:)' \
  "$VERIFY_DIR/node_modules/@yudream/plugin-sdk/package.json" \
  "$VERIFY_DIR/node_modules/@yudream/components/package.json" >/dev/null 2>&1; then
  fail "installed npm contract package manifests must not keep workspace/catalog/link/file protocols"
fi

if grep -R -n -E --exclude-dir=node_modules '(packages/plugin-sdk|packages/components|\.\./\.\./packages/|core-arco-design-vue|D:/code|D:\\code\\|C:/Users/|C:\\Users\\|\.jdks/)' \
  "$VERIFY_DIR/node_modules/@yudream/plugin-sdk" \
  "$VERIFY_DIR/node_modules/@yudream/components" >/dev/null 2>&1; then
  fail "installed npm contract packages must not contain local core/workspace path references"
fi

echo "[verify-core-npm-contracts] OK"
