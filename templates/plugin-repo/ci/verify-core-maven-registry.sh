#!/usr/bin/env sh
set -eu

CORE_MAVEN_REGISTRY="${CORE_MAVEN_REGISTRY:-https://gitlab.example.com/api/v4/projects/CORE_PROJECT_ID/packages/maven}"
SPI_VERSION="${YUDREAM_PLUGIN_SPI_VERSION:-1.0-SNAPSHOT}"
PACKAGE_USER="${CORE_PACKAGE_USER:-gitlab-ci-token}"

if [ -n "${CORE_PACKAGE_TOKEN:-}" ]; then
  PACKAGE_TOKEN="$CORE_PACKAGE_TOKEN"
elif [ -n "${CI_JOB_TOKEN:-}" ]; then
  PACKAGE_TOKEN="$CI_JOB_TOKEN"
else
  echo "CORE_PACKAGE_TOKEN or CI_JOB_TOKEN is required to verify the core Maven registry"
  exit 1
fi

WORK_ROOT="${CI_PROJECT_DIR:-$(pwd)}"
VERIFY_REPO="${VERIFY_MAVEN_REPO:-$WORK_ROOT/.m2/verify-repository}"
SETTINGS_FILE="$(mktemp "${TMPDIR:-/tmp}/verify-core-maven-XXXXXX.xml")"

trap 'rm -f "$SETTINGS_FILE"' EXIT

rm -rf "$VERIFY_REPO"
mkdir -p "$VERIFY_REPO"

cat > "$SETTINGS_FILE" <<EOF
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
    <servers>
        <server>
            <id>gitlab-maven</id>
            <username>${PACKAGE_USER}</username>
            <password>${PACKAGE_TOKEN}</password>
        </server>
    </servers>

    <profiles>
        <profile>
            <id>gitlab-private</id>
            <repositories>
                <repository>
                    <id>gitlab-maven</id>
                    <url>${CORE_MAVEN_REGISTRY}</url>
                </repository>
            </repositories>
            <pluginRepositories>
                <pluginRepository>
                    <id>gitlab-maven</id>
                    <url>${CORE_MAVEN_REGISTRY}</url>
                </pluginRepository>
            </pluginRepositories>
        </profile>
    </profiles>

    <activeProfiles>
        <activeProfile>gitlab-private</activeProfile>
    </activeProfiles>
</settings>
EOF

echo "[verify-core-maven-registry] resolving online.yudream.base:yudream-plugin-spi:${SPI_VERSION}"

mvn -s "$SETTINGS_FILE" \
  -N \
  "-Dmaven.repo.local=$VERIFY_REPO" \
  "-Dartifact=online.yudream.base:yudream-plugin-spi:${SPI_VERSION}" \
  -Dtransitive=false \
  org.apache.maven.plugins:maven-dependency-plugin:3.8.1:get \
  -B -ntp

echo "[verify-core-maven-registry] OK"
