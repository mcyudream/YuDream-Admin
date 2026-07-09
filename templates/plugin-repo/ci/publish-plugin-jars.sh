#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"
. "$ROOT_DIR/ci/lib/plugin-jar-selection.sh"

fail() {
  echo "[publish-plugin-jars] $1" >&2
  exit 1
}

PACKAGE_NAME="${PLUGIN_GENERIC_PACKAGE_NAME:-yudream-admin-plugins}"
PACKAGE_VERSION="${PLUGIN_PACKAGE_VERSION:-${CI_COMMIT_TAG:-}}"
API_BASE="${CI_API_V4_URL:-}"
PROJECT_ID="${CI_PROJECT_ID:-}"
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

if [ -z "$DRY_RUN" ] && [ -z "${CI_JOB_TOKEN:-}" ]; then
  fail "CI_JOB_TOKEN is required unless DRY_RUN is set"
fi

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT INT TERM

JAR_LIST="$TMP_DIR/jars.txt"
MANIFEST_PATH="$TMP_DIR/plugins.manifest.tsv"
CHECKSUM_PATH="$TMP_DIR/sha256sum.txt"

: > "$JAR_LIST"
if ! write_final_plugin_jars "$ROOT_DIR" "$JAR_LIST"; then
  fail "no plugin jars found under yudream-plugins/*/target"
fi

: > "$MANIFEST_PATH"
: > "$CHECKSUM_PATH"

while IFS= read -r jar_path; do
  file_name=$(basename "$jar_path")
  sha256=$(sha256sum "$jar_path" | awk '{print $1}')
  printf '%s  %s\n' "$sha256" "$file_name" >> "$CHECKSUM_PATH"
  printf '%s\t%s\t%s\n' "$file_name" "$sha256" "$jar_path" >> "$MANIFEST_PATH"
done < "$JAR_LIST"

PACKAGE_BASE_URL="$API_BASE/projects/$PROJECT_ID/packages/generic/$PACKAGE_NAME/$PACKAGE_VERSION"

upload_file() {
  source_path=$1
  target_name=$2
  target_url="$PACKAGE_BASE_URL/$target_name"

  if [ -n "$DRY_RUN" ]; then
    echo "[publish-plugin-jars] dry-run upload $source_path -> $target_url"
    return 0
  fi

  echo "[publish-plugin-jars] uploading $target_name"
  curl --fail --location --header "JOB-TOKEN: $CI_JOB_TOKEN" --upload-file "$source_path" "$target_url"
}

while IFS= read -r jar_path; do
  upload_file "$jar_path" "$(basename "$jar_path")"
done < "$JAR_LIST"

upload_file "$CHECKSUM_PATH" "sha256sum.txt"
upload_file "$MANIFEST_PATH" "plugins.manifest.tsv"

echo "[publish-plugin-jars] published package: $PACKAGE_BASE_URL"
