#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"
. "$ROOT_DIR/ci/lib/plugin-jar-selection.sh"

fail() {
  echo "[publish-plugin-jars] $1" >&2
  exit 1
}

REPOSITORY_URL="${NEXUS_MAVEN_RELEASES_URL:-https://nexus.yudream.online/repository/maven-releases}"
PUBLIC_URL="${NEXUS_MAVEN_PUBLIC_URL:-https://nexus.yudream.online/repository/maven-public}"
PACKAGE_VERSION="${PLUGIN_PACKAGE_VERSION:-${CI_COMMIT_TAG:-}}"
DRY_RUN="${DRY_RUN:-}"
PACKAGE_VERSION=${PACKAGE_VERSION#v}

[ -n "$PACKAGE_VERSION" ] || fail "CI_COMMIT_TAG or PLUGIN_PACKAGE_VERSION is required"
if [ -z "$DRY_RUN" ]; then
  [ -n "${NEXUS_USERNAME:-}" ] || fail "NEXUS_USERNAME is required"
  [ -n "${NEXUS_PASSWORD:-}" ] || fail "NEXUS_PASSWORD is required"
fi

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT INT TERM
JAR_LIST="$TMP_DIR/jars.txt"
MANIFEST_PATH="$TMP_DIR/plugins.manifest.tsv"
CHECKSUM_PATH="$TMP_DIR/sha256sum.txt"
SETTINGS_FILE="$TMP_DIR/settings.xml"

write_final_plugin_jars "$ROOT_DIR" "$JAR_LIST" || fail "no plugin jars found under dist/plugins or yudream-plugins/*/target"
: > "$MANIFEST_PATH"
: > "$CHECKSUM_PATH"

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

cat > "$SETTINGS_FILE" <<'EOF'
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <servers>
    <server>
      <id>nexus-releases</id>
      <username>${env.NEXUS_USERNAME}</username>
      <password>${env.NEXUS_PASSWORD}</password>
    </server>
    <server>
      <id>nexus-public</id>
      <username>${env.NEXUS_USERNAME}</username>
      <password>${env.NEXUS_PASSWORD}</password>
    </server>
  </servers>
  <mirrors>
    <mirror>
      <id>aliyun-public</id>
      <mirrorOf>external:*,!nexus-public,!nexus-releases,!nexus-snapshots</mirrorOf>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
EOF

deploy_file() {
  file_path=$1
  artifact_id=$2
  packaging=$3
  shift 3
  if [ -n "$DRY_RUN" ]; then
    echo "[publish-plugin-jars] dry-run deploy online.yudream.plugins:${artifact_id}:${PACKAGE_VERSION}:${packaging}"
    return 0
  fi
  NEXUS_MAVEN_PUBLIC_URL="$PUBLIC_URL" mvn -s "$SETTINGS_FILE" -N org.apache.maven.plugins:maven-deploy-plugin:3.1.4:deploy-file \
    "-DrepositoryId=nexus-releases" "-Durl=$REPOSITORY_URL" \
    "-DgroupId=online.yudream.plugins" "-DartifactId=$artifact_id" \
    "-Dversion=$PACKAGE_VERSION" "-Dpackaging=$packaging" "-Dfile=$file_path" \
    -DgeneratePom=true "$@" -B -ntp
}

while IFS= read -r jar_path; do
  artifact_id=$(resolve_artifact_id "$jar_path") || fail "unable to map plugin jar to artifactId: $jar_path"
  sha256=$(sha256sum "$jar_path" | awk '{print $1}')
  deployed_name="${artifact_id}-${PACKAGE_VERSION}.jar"
  printf '%s  %s\n' "$sha256" "$deployed_name" >> "$CHECKSUM_PATH"
  printf '%s\t%s\t%s\t%s\n' "$artifact_id" "$PACKAGE_VERSION" "$sha256" "$deployed_name" >> "$MANIFEST_PATH"
  deploy_file "$jar_path" "$artifact_id" jar
done < "$JAR_LIST"

deploy_file "$MANIFEST_PATH" plugin-catalog tsv \
  "-Dfiles=$CHECKSUM_PATH" -Dclassifiers=sha256 -Dtypes=txt

echo "[publish-plugin-jars] published Maven version $PACKAGE_VERSION to $REPOSITORY_URL"
