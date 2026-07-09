#!/usr/bin/env sh
set -eu

if [ -z "${CI_API_V4_URL:-}" ] || [ -z "${CI_PROJECT_ID:-}" ]; then
  echo "CI_API_V4_URL and CI_PROJECT_ID are required"
  exit 1
fi

SPI_VERSION="${YUDREAM_PLUGIN_SPI_VERSION:-1.0-SNAPSHOT}"
PACKAGE_REGISTRY_URL="${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/packages/maven"

if [ -n "${CI_DEPLOY_USER:-}" ] && [ -n "${CI_DEPLOY_PASSWORD:-}" ]; then
  PACKAGE_USER="$CI_DEPLOY_USER"
  PACKAGE_TOKEN="$CI_DEPLOY_PASSWORD"
elif [ -n "${CI_JOB_TOKEN:-}" ]; then
  PACKAGE_USER="gitlab-ci-token"
  PACKAGE_TOKEN="$CI_JOB_TOKEN"
else
  echo "CI_DEPLOY_USER/CI_DEPLOY_PASSWORD or CI_JOB_TOKEN is required"
  exit 1
fi

WORK_ROOT="${CI_PROJECT_DIR:-$(pwd)}"
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
                    <url>${PACKAGE_REGISTRY_URL}</url>
                </repository>
            </repositories>
            <pluginRepositories>
                <pluginRepository>
                    <id>gitlab-maven</id>
                    <url>${PACKAGE_REGISTRY_URL}</url>
                </pluginRepository>
            </pluginRepositories>
        </profile>
    </profiles>

    <activeProfiles>
        <activeProfile>gitlab-private</activeProfile>
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
