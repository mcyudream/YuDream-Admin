#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"
. "$ROOT_DIR/ci/lib/plugin-jar-selection.sh"

fail() {
  echo "[verify-published-plugin-jars] $1" >&2
  exit 1
}

PACKAGE_NAME="${PLUGIN_GENERIC_PACKAGE_NAME:-yudream-admin-plugins}"
PACKAGE_VERSION="${PLUGIN_PACKAGE_VERSION:-${CI_COMMIT_TAG:-}}"
API_BASE="${CI_API_V4_URL:-}"
PROJECT_ID="${CI_PROJECT_ID:-}"
READ_TOKEN="${PACKAGE_READ_TOKEN:-${CI_JOB_TOKEN:-}}"
READ_HEADER="${PACKAGE_READ_HEADER:-JOB-TOKEN}"
DRY_RUN="${DRY_RUN:-}"

if [ -z "$API_BASE" ]; then
  fail "CI_API_V4_URL is required"
fi

if [ -z "$PROJECT_ID" ]; then
  fail "CI_PROJECT_ID is required"
fi

if [ -z "$PACKAGE_VERSION" ]; then
  fail "CI_COMMIT_TAG or PLUGIN_PACKAGE_VERSION is required"
fi

if [ -z "$DRY_RUN" ] && [ -z "$READ_TOKEN" ]; then
  fail "CI_JOB_TOKEN or PACKAGE_READ_TOKEN is required unless DRY_RUN is set"
fi

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT INT TERM

JAR_LIST="$TMP_DIR/jars.txt"
LOCAL_CHECKSUM_PATH="$TMP_DIR/local.sha256sum.txt"
LOCAL_MANIFEST_PATH="$TMP_DIR/local.plugins.manifest.tsv"
REMOTE_CHECKSUM_PATH="$TMP_DIR/remote.sha256sum.txt"
REMOTE_MANIFEST_PATH="$TMP_DIR/remote.plugins.manifest.tsv"

if ! write_final_plugin_jars "$ROOT_DIR" "$JAR_LIST"; then
  fail "no plugin jars found under yudream-plugins/*/target"
fi

PACKAGE_BASE_URL="$API_BASE/projects/$PROJECT_ID/packages/generic/$PACKAGE_NAME/$PACKAGE_VERSION"

download_file() {
  source_url=$1
  target_path=$2

  if [ -n "$DRY_RUN" ]; then
    echo "[verify-published-plugin-jars] dry-run download $source_url"
    return 0
  fi

  curl --fail --location --header "$READ_HEADER: $READ_TOKEN" --output "$target_path" "$source_url"
}

: > "$LOCAL_CHECKSUM_PATH"
: > "$LOCAL_MANIFEST_PATH"

while IFS= read -r jar_path; do
  file_name=$(basename "$jar_path")
  sha256=$(sha256sum "$jar_path" | awk '{print $1}')
  printf '%s  %s\n' "$sha256" "$file_name" >> "$LOCAL_CHECKSUM_PATH"
  printf '%s\t%s\t%s\n' "$file_name" "$sha256" "$jar_path" >> "$LOCAL_MANIFEST_PATH"
done < "$JAR_LIST"

if [ -n "$DRY_RUN" ]; then
  download_file "$PACKAGE_BASE_URL/sha256sum.txt" "$REMOTE_CHECKSUM_PATH"
  download_file "$PACKAGE_BASE_URL/plugins.manifest.tsv" "$REMOTE_MANIFEST_PATH"
  while IFS= read -r jar_path; do
    download_file "$PACKAGE_BASE_URL/$(basename "$jar_path")" "$TMP_DIR/$(basename "$jar_path")"
  done < "$JAR_LIST"
  echo "[verify-published-plugin-jars] OK (dry-run)"
  exit 0
fi

download_file "$PACKAGE_BASE_URL/sha256sum.txt" "$REMOTE_CHECKSUM_PATH"
download_file "$PACKAGE_BASE_URL/plugins.manifest.tsv" "$REMOTE_MANIFEST_PATH"

if ! cmp -s "$LOCAL_CHECKSUM_PATH" "$REMOTE_CHECKSUM_PATH"; then
  fail "remote sha256sum.txt does not match local plugin outputs"
fi

if ! cmp -s "$LOCAL_MANIFEST_PATH" "$REMOTE_MANIFEST_PATH"; then
  fail "remote plugins.manifest.tsv does not match local plugin outputs"
fi

while IFS= read -r jar_path; do
  file_name=$(basename "$jar_path")
  downloaded_path="$TMP_DIR/$file_name"
  download_file "$PACKAGE_BASE_URL/$file_name" "$downloaded_path"

  local_sha256=$(sha256sum "$jar_path" | awk '{print $1}')
  remote_sha256=$(sha256sum "$downloaded_path" | awk '{print $1}')
  if [ "$local_sha256" != "$remote_sha256" ]; then
    fail "downloaded jar checksum mismatch: $file_name"
  fi
done < "$JAR_LIST"

echo "[verify-published-plugin-jars] OK"
