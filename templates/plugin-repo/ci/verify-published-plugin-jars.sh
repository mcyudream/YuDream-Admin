#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"
. "$ROOT_DIR/ci/lib/plugin-jar-selection.sh"

fail() {
  echo "[verify-published-plugin-jars] $1" >&2
  exit 1
}

REPOSITORY_URL="${NEXUS_MAVEN_PUBLIC_URL:-https://nexus.yudream.online/repository/maven-public}"
PACKAGE_VERSION="${PLUGIN_PACKAGE_VERSION:-${CI_COMMIT_TAG:-}}"
DRY_RUN="${DRY_RUN:-}"
PACKAGE_VERSION=${PACKAGE_VERSION#v}

[ -n "$PACKAGE_VERSION" ] || fail "CI_COMMIT_TAG or PLUGIN_PACKAGE_VERSION is required"

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT INT TERM
JAR_LIST="$TMP_DIR/jars.txt"
LOCAL_MANIFEST="$TMP_DIR/local.plugins.manifest.tsv"
LOCAL_CHECKSUM="$TMP_DIR/local.sha256sum.txt"
VERIFY_REPO="$TMP_DIR/repository"
SETTINGS_FILE="$TMP_DIR/settings.xml"

write_final_plugin_jars "$ROOT_DIR" "$JAR_LIST" || fail "no plugin jars found under dist/plugins or yudream-plugins/*/target"
: > "$LOCAL_MANIFEST"
: > "$LOCAL_CHECKSUM"

resolve_artifact_id() {
  file_name=$(basename "$1")
  for module_dir in "$ROOT_DIR"/yudream-plugins/*; do
    [ -d "$module_dir" ] || continue
    artifact_id=$(basename "$module_dir")
    case "$file_name" in
      "$artifact_id"-*.jar) printf '%s\n' "$artifact_id"; return 0 ;;
    esac
  done
  return 1
}

cat > "$SETTINGS_FILE" <<EOF
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <mirrors>
    <mirror>
      <id>nexus-public</id>
      <mirrorOf>*</mirrorOf>
      <url>${REPOSITORY_URL}</url>
    </mirror>
  </mirrors>
</settings>
EOF

fetch_artifact() {
  coordinates=$1
  if [ -n "$DRY_RUN" ]; then
    echo "[verify-published-plugin-jars] dry-run resolve $coordinates from $REPOSITORY_URL"
    return 0
  fi
  mvn -s "$SETTINGS_FILE" -N \
    "-Dmaven.repo.local=$VERIFY_REPO" "-Dartifact=$coordinates" -Dtransitive=false \
    org.apache.maven.plugins:maven-dependency-plugin:3.8.1:get -B -ntp
}

while IFS= read -r jar_path; do
  artifact_id=$(resolve_artifact_id "$jar_path") || fail "unable to map plugin jar to artifactId: $jar_path"
  sha256=$(sha256sum "$jar_path" | awk '{print $1}')
  deployed_name="${artifact_id}-${PACKAGE_VERSION}.jar"
  printf '%s  %s\n' "$sha256" "$deployed_name" >> "$LOCAL_CHECKSUM"
  printf '%s\t%s\t%s\t%s\n' "$artifact_id" "$PACKAGE_VERSION" "$sha256" "$deployed_name" >> "$LOCAL_MANIFEST"
  fetch_artifact "online.yudream.plugins:${artifact_id}:${PACKAGE_VERSION}:jar"
  if [ -z "$DRY_RUN" ]; then
    downloaded="$VERIFY_REPO/online/yudream/plugins/$artifact_id/$PACKAGE_VERSION/$deployed_name"
    [ -f "$downloaded" ] || fail "downloaded plugin jar is missing: $deployed_name"
    [ "$sha256" = "$(sha256sum "$downloaded" | awk '{print $1}')" ] || fail "downloaded jar checksum mismatch: $deployed_name"
  fi
done < "$JAR_LIST"

fetch_artifact "online.yudream.plugins:plugin-catalog:${PACKAGE_VERSION}:tsv"
fetch_artifact "online.yudream.plugins:plugin-catalog:${PACKAGE_VERSION}:txt:sha256"

if [ -n "$DRY_RUN" ]; then
  echo "[verify-published-plugin-jars] OK (dry-run)"
  exit 0
fi

CATALOG_DIR="$VERIFY_REPO/online/yudream/plugins/plugin-catalog/$PACKAGE_VERSION"
cmp -s "$LOCAL_MANIFEST" "$CATALOG_DIR/plugin-catalog-${PACKAGE_VERSION}.tsv" || fail "published plugin catalog does not match local outputs"
cmp -s "$LOCAL_CHECKSUM" "$CATALOG_DIR/plugin-catalog-${PACKAGE_VERSION}-sha256.txt" || fail "published checksum catalog does not match local outputs"

echo "[verify-published-plugin-jars] OK"
