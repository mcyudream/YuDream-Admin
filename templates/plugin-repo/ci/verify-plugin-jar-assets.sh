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

read_archive_entry() {
  archive_path=$1
  entry_path=$2
  if command -v unzip >/dev/null 2>&1; then
    unzip -p "$archive_path" "$entry_path"
    return 0
  fi
  if command -v tar >/dev/null 2>&1; then
    tar -xOf "$archive_path" "$entry_path"
    return 0
  fi
  fail "unzip or tar command is required to inspect frontend assets"
}

check_frontend_asset_references() {
  archive_path=$1
  frontend_root=$2
  entry_path="${frontend_root}/remoteEntry.js"
  entry_content=$(read_archive_entry "$archive_path" "$entry_path") \
    || fail "cannot read frontend entry: $entry_path in $archive_path"

  if printf '%s\n' "$entry_content" | grep -Eq "from[[:space:]]*[\"']/|import\\([[:space:]]*[\"']/|new[[:space:]]+URL\\([[:space:]]*[\"']/"; then
    fail "remote entry must not use host-root absolute asset paths: $entry_path in $archive_path"
  fi
  if printf '%s\n' "$entry_content" | grep -Eq "from[[:space:]]*[\"']\\.\\./|import\\([[:space:]]*[\"']\\.\\./|new[[:space:]]+URL\\([[:space:]]*[\"']\\.\\./"; then
    fail "remote entry must not use parent-relative asset paths: $entry_path in $archive_path"
  fi

  stylesheet_paths=$(printf '%s\n' "$archive_listing" | grep -E "^${frontend_root}/.*\.css$" || true)
  [ -n "$stylesheet_paths" ] || return 0

  while IFS= read -r asset_path; do
    asset_content=$(read_archive_entry "$archive_path" "$asset_path") \
      || fail "cannot read frontend stylesheet: $asset_path in $archive_path"
    if printf '%s\n' "$asset_content" | grep -Eq "@import[[:space:]]+[\"']/|url\\([[:space:]]*[\"']?/"; then
      fail "stylesheet must not use host-root absolute asset paths: $asset_path in $archive_path"
    fi
    if printf '%s\n' "$asset_content" | grep -Eq "@import[[:space:]]+[\"']\\.\\./|url\\([[:space:]]*[\"']?\\.\\./"; then
      fail "stylesheet must not use parent-relative asset paths: $asset_path in $archive_path"
    fi
  done <<EOF
$stylesheet_paths
EOF
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
  if printf '%s\n' "$archive_listing" | grep -Eq '(^|/)\.\.(/|$)|\\'; then
    fail "plugin jar frontend asset paths must not contain backslashes or path traversal: $jar_path"
  fi

  printf '%s\n' "$archive_listing" \
    | sed -n 's#^\(META-INF/yudream-plugin/frontend/[^/]\+\)/remoteEntry\.js$#\1#p' \
    | while IFS= read -r frontend_root; do
        check_frontend_asset_references "$jar_path" "$frontend_root"
      done
done < "$JAR_LIST"

echo "[verify-plugin-jar-assets] OK"
