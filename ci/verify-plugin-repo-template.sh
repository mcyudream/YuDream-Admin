#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

fail() {
  echo "[verify-plugin-repo-template] $1" >&2
  exit 1
}

require_file() {
  file=$1
  if [ ! -f "$file" ]; then
    fail "missing template file: $file"
  fi
}

echo "[verify-plugin-repo-template] checking required template files"
require_file "templates/plugin-repo/.gitlab-ci.yml.example"
require_file "templates/plugin-repo/.npmrc.example"
require_file "templates/plugin-repo/settings.xml.example"
require_file "templates/plugin-repo/pnpm-workspace.yaml.example"
require_file "templates/plugin-repo/README.md"
require_file "templates/plugin-repo/docs/plugin-release.md"
require_file "templates/plugin-repo/ci/verify-plugin-repo-independence.sh"
require_file "templates/plugin-repo/ci/stage-plugin-repo-foundation.sh"
require_file "templates/plugin-repo/ci/stage-plugin-source-migration.sh"
require_file "templates/plugin-repo/ci/show-plugin-repo-status.sh"
require_file "templates/plugin-repo/ci/verify-plugin-repo-readiness.sh"
require_file "templates/plugin-repo/ci/verify-plugin-remote-release-evidence.sh"
require_file "templates/plugin-repo/ci/verify-core-maven-registry.sh"
require_file "templates/plugin-repo/ci/verify-plugin-maven-boundary.sh"
require_file "templates/plugin-repo/ci/verify-core-npm-contracts.sh"
require_file "templates/plugin-repo/ci/verify-doc-independence.sh"
require_file "templates/plugin-repo/ci/verify-plugin-publish-pipeline.sh"
require_file "templates/plugin-repo/ci/lib/plugin-jar-selection.sh"
require_file "templates/plugin-repo/ci/verify-plugin-jar-assets.sh"
require_file "templates/plugin-repo/ci/publish-plugin-jars.sh"
require_file "templates/plugin-repo/ci/verify-published-plugin-jars.sh"

echo "[verify-plugin-repo-template] checking CI example hooks"
grep -q 'sh ci/verify-core-npm-contracts.sh' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must verify core npm contracts"
grep -q 'sh ci/verify-doc-independence.sh' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must verify documentation independence"
grep -q 'sh ci/verify-plugin-maven-boundary.sh' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must verify plugin maven boundary"
grep -q 'sh ci/verify-plugin-publish-pipeline.sh' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must verify plugin publish pipeline"
grep -q 'sh ci/verify-plugin-jar-assets.sh' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must verify plugin jar assets"
grep -q 'copy_final_plugin_jars "\$PWD" "\$PWD/dist/plugins"' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must flatten final plugin jars into dist/plugins"
grep -q 'sh ci/publish-plugin-jars.sh' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must publish plugin jars"
grep -q 'sh ci/verify-published-plugin-jars.sh' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must verify published plugin jars"
grep -q 'PACKAGE_MAVEN_REPO' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI package job must use a dedicated clean Maven local repository"
grep -Eq '^[[:space:]]*-[[:space:]]+yudream-frontend/packages/plugin-\*/package\.json$' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must restrict frontend job discovery to plugin packages"
grep -q 'pnpm -r --filter=@yudream/plugin-\* run build' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI frontend build must explicitly filter @yudream/plugin-* packages"
grep -Eq '^[[:space:]]*-[[:space:]]+yudream-frontend/packages/plugin-\*/dist/$' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI artifacts must stay limited to plugin package dist outputs"
grep -Eq '^[[:space:]]*-[[:space:]]+dist/plugins/\*\.jar$' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must export flat plugin jars from dist/plugins"
if grep -Eq '^[[:space:]]*-[[:space:]]+yudream-frontend/packages/\*/package\.json$' templates/plugin-repo/.gitlab-ci.yml.example; then
  fail "template CI must not use yudream-frontend/packages/*/package.json"
fi

echo "[verify-plugin-repo-template] checking workspace boundary example"
grep -Eq '^[[:space:]]*-[[:space:]]+packages/plugin-\*$' templates/plugin-repo/pnpm-workspace.yaml.example || fail "template pnpm workspace must restrict packages to packages/plugin-*"
if grep -Eq '^[[:space:]]*-[[:space:]]+packages/\*$' templates/plugin-repo/pnpm-workspace.yaml.example; then
  fail "template pnpm workspace must not use packages/*"
fi

echo "[verify-plugin-repo-template] checking npm registry example"
grep -q '^registry=https://registry.npmjs.org/$' templates/plugin-repo/.npmrc.example || fail "template .npmrc must keep a public registry for third-party packages"
grep -q '^@yudream:registry=https://nexus.yudream.online/repository/npm-public/$' templates/plugin-repo/.npmrc.example || fail "template @yudream scope must use Nexus npm-public"
grep -q "'@yudream/components': 1.0.0$" templates/plugin-repo/pnpm-workspace.yaml.example || fail "template must consume stable @yudream/components 1.0.0"
grep -q "'@yudream/plugin-sdk': 1.0.1$" templates/plugin-repo/pnpm-workspace.yaml.example || fail "template must consume stable @yudream/plugin-sdk 1.0.1"
grep -q 'NEXUS_MAVEN_PUBLIC_URL: "https://nexus.yudream.online/repository/maven-public/"' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must use Nexus maven-public"
grep -q 'NEXUS_NPM_PUBLIC_URL: "https://nexus.yudream.online/repository/npm-public/"' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must use Nexus npm-public"
grep -q 'NEXUS_USERNAME' templates/plugin-repo/ci/publish-plugin-jars.sh || fail "template publishing must require NEXUS_USERNAME"
grep -q 'NEXUS_PASSWORD' templates/plugin-repo/ci/publish-plugin-jars.sh || fail "template publishing must require NEXUS_PASSWORD"
if grep -Eq 'NEXUS_(USERNAME|PASSWORD)' templates/plugin-repo/ci/verify-core-maven-registry.sh templates/plugin-repo/ci/verify-core-npm-contracts.sh templates/plugin-repo/ci/verify-published-plugin-jars.sh; then
  fail "template read and verification paths must not require protected publish credentials"
fi
if grep -q '<mirrorOf>' templates/plugin-repo/.gitlab-ci.yml.example templates/plugin-repo/settings.xml.example; then
  fail "template must preserve explicit Aliyun-to-Nexus repository ordering"
fi
grep -q 'https://maven.aliyun.com/repository/public' templates/plugin-repo/.gitlab-ci.yml.example || fail "template must resolve third-party Maven dependencies from Aliyun"
grep -q '<id>nexus-plugin</id>' templates/plugin-repo/settings.xml.example || fail "template Maven plugins must fall back from Aliyun to Nexus"
grep -q '<id>nexus-plugin</id>' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI Maven plugins must fall back from Aliyun to Nexus"
for script in publish-plugin-jars.sh verify-core-maven-registry.sh verify-published-plugin-jars.sh; do
  grep -q '<id>nexus-plugin</id>' "templates/plugin-repo/ci/$script" \
    || fail "template $script Maven plugins must fall back from Aliyun to Nexus"
done
grep -Fq '<url>${env.NEXUS_MAVEN_PUBLIC_URL}</url>' templates/plugin-repo/ci/publish-plugin-jars.sh \
  || fail "template publish settings must pass the Nexus plugin fallback URL through Maven environment interpolation"
if grep -Eq 'maven-dependency-plugin[^[:space:]]*:get|dependency:get|remoteRepositories=' templates/plugin-repo/.gitlab-ci.yml.example; then
  fail "template CI must not prefetch Maven artifacts outside the configured repository order"
fi
grep -q 'remoteRepositories=nexus-public' templates/plugin-repo/ci/verify-core-maven-registry.sh || fail "template SPI verification must explicitly resolve YuDream artifacts from Nexus"
grep -q 'remoteRepositories=nexus-public' templates/plugin-repo/ci/verify-published-plugin-jars.sh || fail "template plugin JAR verification must explicitly resolve YuDream artifacts from Nexus"
grep -q 'yudream\.plugin\.spi\.version' templates/plugin-repo/ci/verify-core-maven-registry.sh || fail "template Maven verification must derive the SPI version from the plugin root POM"
if grep -q 'YUDREAM_PLUGIN_SPI_VERSION:-1.0-SNAPSHOT' templates/plugin-repo/ci/verify-core-maven-registry.sh; then
  fail "template Maven verification must not default to a hard-coded SPI snapshot"
fi
if grep -R -Eq 'gitlab-maven|gitlab\.(example\.com|yudream\.online)/api/v4/projects|CI_JOB_TOKEN|CORE_PACKAGE_(USER|TOKEN)|packages/(maven|npm)' \
  templates/plugin-repo/.gitlab-ci.yml.example \
  templates/plugin-repo/.npmrc.example \
  templates/plugin-repo/settings.xml.example \
  templates/plugin-repo/ci/publish-plugin-jars.sh \
  templates/plugin-repo/ci/verify-core-maven-registry.sh \
  templates/plugin-repo/ci/verify-core-npm-contracts.sh \
  templates/plugin-repo/ci/verify-published-plugin-jars.sh; then
  fail "plugin repository templates must use only Nexus package endpoints and credentials"
fi

echo "[verify-plugin-repo-template] checking template docs for local absolute paths"
if grep -R -n -E '(/D:/code|D:/code|D:\\code\\|C:/Users/|C:\\Users\\|\.jdks/)' templates/plugin-repo/README.md templates/plugin-repo/docs >/dev/null 2>&1; then
  fail "template documentation must not contain local machine absolute paths"
fi

echo "[verify-plugin-repo-template] OK"
