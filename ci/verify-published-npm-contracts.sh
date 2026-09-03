#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

fail() {
  echo "[verify-published-npm-contracts] $1" >&2
  exit 1
}

if ! command -v pnpm >/dev/null 2>&1; then
  fail "pnpm is required"
fi

# 分包发布 tag（psdk-*/pcomp-*）经 CI rules 注入 VERIFY_NPM_PACKAGES 限定验签范围；
# 默认验签全部 npm 契约包。
VERIFY_PACKAGES="${VERIFY_NPM_PACKAGES:-@yudream/plugin-sdk @yudream/components}"

DEPS_JSON=""
PKG_SUMMARY=""
for pkg in $VERIFY_PACKAGES; do
  case "$pkg" in
    "@yudream/plugin-sdk") PKG_DIR="plugin-sdk" ;;
    "@yudream/components") PKG_DIR="components" ;;
    *) fail "unsupported package in VERIFY_NPM_PACKAGES: $pkg" ;;
  esac
  PKG_VERSION=$(sed -n 's/^[[:space:]]*"version":[[:space:]]*"\([^"]*\)".*/\1/p' "yudream-frontend/packages/$PKG_DIR/package.json" | head -n 1)
  [ -n "$PKG_VERSION" ] || fail "unable to resolve $pkg version from package.json"
  DEPS_JSON="${DEPS_JSON}
    \"${pkg}\": \"${PKG_VERSION}\","
  PKG_SUMMARY="${PKG_SUMMARY} ${pkg}@${PKG_VERSION}"
done

TARGET_REGISTRY="${VERIFY_NPM_REGISTRY:-${NEXUS_NPM_PUBLIC_URL:-https://nexus.yudream.online/repository/npm-public/}}"
ATTEMPTS="${VERIFY_NPM_ATTEMPTS:-12}"
SLEEP_SECONDS="${VERIFY_NPM_SLEEP_SECONDS:-10}"

if [ -z "${NEXUS_USERNAME:-}" ] || [ -z "${NEXUS_PASSWORD:-}" ]; then
  fail "NEXUS_USERNAME and NEXUS_PASSWORD are required"
fi

VERIFY_DIR="${VERIFY_PUBLISHED_NPM_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/yudream-published-npm-XXXXXX")}"
trap 'rm -rf "$VERIFY_DIR"' EXIT INT TERM

mkdir -p "$VERIFY_DIR"

cat > "$VERIFY_DIR/package.json" <<EOF
{
  "name": "verify-yudream-published-npm-contracts",
  "private": true,
  "version": "0.0.0",
  "packageManager": "pnpm@11.9.0",
  "dependencies": {${DEPS_JSON}
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
//${REGISTRY_HOST}/:username=${NEXUS_USERNAME}
//${REGISTRY_HOST}/:_password=$(printf '%s' "$NEXUS_PASSWORD" | base64 | tr -d '\n')
always-auth=true
EOF

attempt=1
while :; do
  echo "[verify-published-npm-contracts] attempt ${attempt}/${ATTEMPTS}: install${PKG_SUMMARY} from ${TARGET_REGISTRY}"
  if pnpm --dir "$VERIFY_DIR" install --lockfile=false --ignore-scripts --config.strict-peer-dependencies=false >/dev/null 2>&1; then
    break
  fi

  if [ "$attempt" -ge "$ATTEMPTS" ]; then
    fail "unable to install published npm contract packages from ${TARGET_REGISTRY}"
  fi

  attempt=$((attempt + 1))
  rm -rf "$VERIFY_DIR/node_modules" "$VERIFY_DIR/pnpm-lock.yaml"
  sleep "$SLEEP_SECONDS"
done

PKG_MANIFESTS=""
PKG_DIRS=""
for pkg in $VERIFY_PACKAGES; do
  PKG_MANIFESTS="$PKG_MANIFESTS $VERIFY_DIR/node_modules/$pkg/package.json"
  PKG_DIRS="$PKG_DIRS $VERIFY_DIR/node_modules/$pkg"
  if [ "$pkg" = "@yudream/plugin-sdk" ]; then
    [ -f "$VERIFY_DIR/node_modules/@yudream/plugin-sdk/vite-shared.js" ] || fail "installed @yudream/plugin-sdk is missing vite-shared.js"
    [ -f "$VERIFY_DIR/node_modules/@yudream/plugin-sdk/vite-shared.d.ts" ] || fail "installed @yudream/plugin-sdk is missing vite-shared.d.ts"
  fi
  if [ "$pkg" = "@yudream/components" ]; then
    [ -f "$VERIFY_DIR/node_modules/@yudream/components/resolver.ts" ] || fail "installed @yudream/components is missing resolver.ts"
  fi
done

# PKG_MANIFESTS/PKG_DIRS 需要按空格分词展开成多个路径参数
if grep -R -n -E '(workspace:|catalog:|link:|file:)' $PKG_MANIFESTS >/dev/null 2>&1; then
  fail "published npm contract package manifests must not keep workspace/catalog/link/file protocols"
fi

if grep -R -n -E --exclude-dir=node_modules '(packages/plugin-sdk|packages/components|\.\./\.\./packages/|core-arco-design-vue|D:/code|D:\\code\\|C:/Users/|C:\\Users\\|\.jdks/)' $PKG_DIRS >/dev/null 2>&1; then
  fail "published npm contract packages must not contain local core/workspace path references"
fi

echo "[verify-published-npm-contracts] OK"
