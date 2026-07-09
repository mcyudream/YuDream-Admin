#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

fail() {
  echo "[verify-contract-package-tarballs] $1" >&2
  exit 1
}

if ! command -v pnpm >/dev/null 2>&1; then
  fail "pnpm is required"
fi

WORK_ROOT="${CI_PROJECT_DIR:-$ROOT_DIR}"
PACK_DIR="${VERIFY_NPM_PACK_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/yudream-npm-pack-XXXXXX")}"
EXTRACT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/yudream-npm-pack-extract-XXXXXX")"

trap 'rm -rf "$PACK_DIR" "$EXTRACT_DIR"' EXIT INT TERM

mkdir -p "$PACK_DIR"

echo "[verify-contract-package-tarballs] packing @yudream/plugin-sdk"
pnpm --config.engine-strict=false --dir yudream-frontend --filter @yudream/plugin-sdk pack --pack-destination "$PACK_DIR" >/dev/null

echo "[verify-contract-package-tarballs] packing @yudream/components"
pnpm --config.engine-strict=false --dir yudream-frontend --filter @yudream/components pack --pack-destination "$PACK_DIR" >/dev/null

PLUGIN_SDK_TGZ=$(find "$PACK_DIR" -maxdepth 1 -type f -name 'yudream-plugin-sdk-*.tgz' | head -n 1)
COMPONENTS_TGZ=$(find "$PACK_DIR" -maxdepth 1 -type f -name 'yudream-components-*.tgz' | head -n 1)

[ -n "$PLUGIN_SDK_TGZ" ] || fail "missing plugin-sdk packed tarball"
[ -n "$COMPONENTS_TGZ" ] || fail "missing components packed tarball"

extract_tgz() {
  archive_path=$1
  target_dir=$2
  rm -rf "$target_dir"
  mkdir -p "$target_dir"
  tar -xf "$archive_path" -C "$target_dir"
}

assert_no_publish_local_refs() {
  label=$1
  base_dir=$2

  if grep -R -n -E '(workspace:|catalog:|link:|file:)' "$base_dir/package/package.json" >/dev/null 2>&1; then
    fail "$label tarball package.json must not keep workspace/catalog/link/file protocols"
  fi

  if grep -R -n -E '(packages/plugin-sdk|packages/components|\.\./\.\./packages/|core-arco-design-vue|D:/code|D:\\code\\|C:/Users/|C:\\Users\\|\.jdks/)' "$base_dir/package" >/dev/null 2>&1; then
    fail "$label tarball must not contain local core/workspace path references"
  fi
}

echo "[verify-contract-package-tarballs] checking plugin-sdk tarball contents"
tar -tf "$PLUGIN_SDK_TGZ" | grep -q '^package/vite-shared\.js$' || fail "plugin-sdk tarball must contain vite-shared.js"
tar -tf "$PLUGIN_SDK_TGZ" | grep -q '^package/vite-shared\.d\.ts$' || fail "plugin-sdk tarball must contain vite-shared.d.ts"
tar -tf "$PLUGIN_SDK_TGZ" | grep -q '^package/src/host-vue\.ts$' || fail "plugin-sdk tarball must contain src/host-vue.ts"
tar -tf "$PLUGIN_SDK_TGZ" | grep -q '^package/src/host-vue-router\.ts$' || fail "plugin-sdk tarball must contain src/host-vue-router.ts"
tar -tf "$PLUGIN_SDK_TGZ" | grep -q '^package/src/host-components\.ts$' || fail "plugin-sdk tarball must contain src/host-components.ts"
extract_tgz "$PLUGIN_SDK_TGZ" "$EXTRACT_DIR/plugin-sdk"
grep -q '"registry":[[:space:]]*"https://registry.npmjs.org/"' "$EXTRACT_DIR/plugin-sdk/package/package.json" || fail "plugin-sdk tarball package.json must publish to npmjs by default"
assert_no_publish_local_refs "@yudream/plugin-sdk" "$EXTRACT_DIR/plugin-sdk"

echo "[verify-contract-package-tarballs] checking components tarball contents"
tar -tf "$COMPONENTS_TGZ" | grep -q '^package/resolver\.ts$' || fail "components tarball must contain resolver.ts"
tar -tf "$COMPONENTS_TGZ" | grep -q '^package/src/index\.ts$' || fail "components tarball must contain src/index.ts"
extract_tgz "$COMPONENTS_TGZ" "$EXTRACT_DIR/components"
grep -q '"registry":[[:space:]]*"https://registry.npmjs.org/"' "$EXTRACT_DIR/components/package/package.json" || fail "components tarball package.json must publish to npmjs by default"
assert_no_publish_local_refs "@yudream/components" "$EXTRACT_DIR/components"

echo "[verify-contract-package-tarballs] OK"
