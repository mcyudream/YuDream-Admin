#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"
. "$ROOT_DIR/ci/lib/plugin-jar-selection.sh"

fail() {
  echo "[verify-plugin-jar-assets] $1" >&2
  exit 1
}

list_archive() {
  archive_path=$1
  if command -v jar >/dev/null 2>&1; then
    jar tf "$archive_path"
    return 0
  fi
  if command -v unzip >/dev/null 2>&1; then
    unzip -Z1 "$archive_path"
    return 0
  fi
  if command -v tar >/dev/null 2>&1; then
    tar -tf "$archive_path"
    return 0
  fi
  fail "jar, unzip, or tar command is required"
}

JAR_LIST=$(mktemp "${TMPDIR:-/tmp}/yudream-plugin-jars-XXXXXX.txt")
trap 'rm -f "$JAR_LIST"' EXIT INT TERM

if ! write_final_plugin_jars "$ROOT_DIR" "$JAR_LIST"; then
  fail "no plugin jars found under yudream-plugins/*/target"
fi

while IFS= read -r jar_path; do
  echo "[verify-plugin-jar-assets] checking $(basename "$jar_path")"
  archive_listing=$(list_archive "$jar_path")
  if ! printf '%s\n' "$archive_listing" | grep -Eq '^META-INF/yudream-plugin/frontend/.+/remoteEntry\.js$'; then
    fail "plugin jar is missing META-INF/yudream-plugin/frontend/*/remoteEntry.js: $jar_path"
  fi
  if printf '%s\n' "$archive_listing" | grep -Eq '^online/yudream/base/plugin/spi/'; then
    fail "plugin jar must not embed core SPI classes: $jar_path"
  fi
done < "$JAR_LIST"

echo "[verify-plugin-jar-assets] OK"
