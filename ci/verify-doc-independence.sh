#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

fail() {
  echo "[verify-doc-independence] $1" >&2
  exit 1
}

check_no_local_paths() {
  target=$1
  if grep -R -n -E '(/D:/code|D:/code|D:\\code\\|C:/Users/|C:\\Users\\|\.jdks/)' "$target" >/dev/null 2>&1; then
    fail "documentation must not contain local machine absolute paths: $target"
  fi
}

echo "[verify-doc-independence] checking active repository docs"
check_no_local_paths "README.md"
check_no_local_paths "docs/repository-split"
check_no_local_paths "docs/plugin-system"
check_no_local_paths "plugins/README.md"
check_no_local_paths "templates/plugin-repo/README.md"
check_no_local_paths "templates/plugin-repo/docs"

echo "[verify-doc-independence] OK"
