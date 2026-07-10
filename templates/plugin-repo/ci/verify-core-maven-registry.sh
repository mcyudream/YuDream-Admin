#!/usr/bin/env sh
set -eu

NEXUS_MAVEN_PUBLIC_URL="${NEXUS_MAVEN_PUBLIC_URL:-https://nexus.yudream.online/repository/maven-public}"
WORK_ROOT="${CI_PROJECT_DIR:-$(pwd)}"
if [ -n "${YUDREAM_PLUGIN_SPI_VERSION:-}" ]; then
  SPI_VERSION="$YUDREAM_PLUGIN_SPI_VERSION"
else
  SPI_VERSION=$(sed -n 's#.*<yudream\.plugin\.spi\.version>\([^<]*\)</yudream\.plugin\.spi\.version>.*#\1#p' "$WORK_ROOT/pom.xml" | head -n 1)
fi
[ -n "$SPI_VERSION" ] || {
  echo "unable to resolve yudream.plugin.spi.version from $WORK_ROOT/pom.xml"
  exit 1
}
VERIFY_REPO="${VERIFY_MAVEN_REPO:-$WORK_ROOT/.m2/verify-repository}"
SETTINGS_FILE="$(mktemp "${TMPDIR:-/tmp}/verify-core-maven-XXXXXX.xml")"

trap 'rm -f "$SETTINGS_FILE"' EXIT

rm -rf "$VERIFY_REPO"
mkdir -p "$VERIFY_REPO"

cat > "$SETTINGS_FILE" <<EOF
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
    <mirrors>
        <mirror>
            <id>aliyun-public</id>
            <mirrorOf>external:*,!nexus-public,!nexus-releases,!nexus-snapshots</mirrorOf>
            <url>https://maven.aliyun.com/repository/public</url>
        </mirror>
    </mirrors>
</settings>
EOF

echo "[verify-core-maven-registry] resolving online.yudream.base:yudream-plugin-spi:${SPI_VERSION}"

mvn -s "$SETTINGS_FILE" \
  -N \
  "-Dmaven.repo.local=$VERIFY_REPO" \
  "-Dartifact=online.yudream.base:yudream-plugin-spi:${SPI_VERSION}" \
  "-DremoteRepositories=nexus-public::default::${NEXUS_MAVEN_PUBLIC_URL}" \
  -Dtransitive=false \
  org.apache.maven.plugins:maven-dependency-plugin:3.8.1:get \
  -B -ntp

echo "[verify-core-maven-registry] OK"
