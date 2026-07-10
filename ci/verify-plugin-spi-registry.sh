#!/usr/bin/env sh
set -eu

if [ -z "${NEXUS_USERNAME:-}" ] || [ -z "${NEXUS_PASSWORD:-}" ]; then
  echo "NEXUS_USERNAME and NEXUS_PASSWORD are required"
  exit 1
fi

WORK_ROOT="${CI_PROJECT_DIR:-$(pwd)}"
SPI_POM="$WORK_ROOT/yudream-plugins/yudream-plugin-spi/pom.xml"
if [ -n "${YUDREAM_PLUGIN_SPI_VERSION:-}" ]; then
  SPI_VERSION="$YUDREAM_PLUGIN_SPI_VERSION"
else
  SPI_VERSION=$(sed -n 's#.*<version>\([^<]*\)</version>.*#\1#p' "$SPI_POM" | head -n 1)
fi
[ -n "$SPI_VERSION" ] || {
  echo "unable to resolve yudream-plugin-spi version from $SPI_POM"
  exit 1
}
PACKAGE_REGISTRY_URL="${NEXUS_MAVEN_PUBLIC_URL:-https://nexus.yudream.online/repository/maven-public/}"

VERIFY_REPO="${VERIFY_MAVEN_REPO:-$WORK_ROOT/.m2/published-verify-repository}"
SETTINGS_FILE="$(mktemp "${TMPDIR:-/tmp}/verify-plugin-spi-XXXXXX.xml")"

trap 'rm -f "$SETTINGS_FILE"' EXIT

rm -rf "$VERIFY_REPO"
mkdir -p "$VERIFY_REPO"

cat > "$SETTINGS_FILE" <<EOF
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
    <servers>
        <server>
            <id>nexus-public</id>
            <username>\${env.NEXUS_USERNAME}</username>
            <password>\${env.NEXUS_PASSWORD}</password>
        </server>
    </servers>

    <profiles>
        <profile>
            <id>nexus</id>
            <repositories>
                <repository>
                    <id>nexus-public</id>
                    <url>${PACKAGE_REGISTRY_URL}</url>
                </repository>
            </repositories>
            <pluginRepositories>
                <pluginRepository>
                    <id>nexus-public</id>
                    <url>${PACKAGE_REGISTRY_URL}</url>
                </pluginRepository>
            </pluginRepositories>
        </profile>
    </profiles>

    <activeProfiles>
        <activeProfile>nexus</activeProfile>
    </activeProfiles>
</settings>
EOF

echo "[verify-plugin-spi-registry] resolving online.yudream.base:yudream-plugin-spi:${SPI_VERSION}"

mvn -s "$SETTINGS_FILE" \
  -N \
  "-Dmaven.repo.local=$VERIFY_REPO" \
  "-Dartifact=online.yudream.base:yudream-plugin-spi:${SPI_VERSION}" \
  -Dtransitive=false \
  org.apache.maven.plugins:maven-dependency-plugin:3.8.1:get \
  -B -ntp

echo "[verify-plugin-spi-registry] OK"
