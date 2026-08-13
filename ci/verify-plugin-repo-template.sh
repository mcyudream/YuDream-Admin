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
require_file "templates/plugin-repo/plugin.yml.example"
require_file "templates/plugin-repo/store.json.example"
require_file "templates/plugin-repo/submission.json.example"
require_file "templates/plugin-repo/LICENSE"
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
require_file "templates/plugin-repo/ci/verify-plugin-release-selection.sh"
require_file "templates/plugin-repo/ci/lib/plugin-jar-selection.sh"
require_file "templates/plugin-repo/release/plugins.txt"
require_file "templates/plugin-repo/ci/verify-plugin-jar-assets.sh"
require_file "templates/plugin-repo/ci/publish-plugin-jars.sh"
require_file "templates/plugin-repo/ci/verify-published-plugin-jars.sh"

echo "[verify-plugin-repo-template] checking CI example hooks"
grep -q 'sh ci/verify-core-npm-contracts.sh' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must verify core npm contracts"
grep -q 'sh ci/verify-doc-independence.sh' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must verify documentation independence"
grep -q 'sh ci/verify-plugin-maven-boundary.sh' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must verify plugin maven boundary"
grep -q 'sh ci/verify-plugin-publish-pipeline.sh' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must verify plugin publish pipeline"
grep -q 'sh ci/verify-plugin-release-selection.sh' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must validate explicit plugin release selection"
grep -q 'sh ci/verify-plugin-jar-assets.sh' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must verify plugin jar assets"
grep -q 'selected_plugin_modules_csv' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI tag package must resolve explicit plugin modules"
grep -q 'clean package -pl "\$release_modules" -am' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI tag package must use Maven -pl selected modules -am"
grep -q 'PLUGIN_RELEASE_ONLY="\${CI_COMMIT_TAG:+1}" copy_final_plugin_jars "\$PWD" "\$PWD/dist/plugins"' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must stage only selected tag jars"
grep -q 'PLUGIN_RELEASE_ONLY=1 sh ci/publish-plugin-jars.sh' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must publish only selected tag jars"
grep -q 'PLUGIN_RELEASE_ONLY=1 sh ci/verify-published-plugin-jars.sh' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must verify only selected tag jars"
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

echo "[verify-plugin-repo-template] checking third-party submission templates"
grep -q '^name: example-plugin$' templates/plugin-repo/plugin.yml.example || fail "submission plugin.yml example must declare its code"
grep -q '^main: com.example.yudream.ExamplePlugin$' templates/plugin-repo/plugin.yml.example || fail "submission plugin.yml example must declare its entry class"
grep -q '^version: 1.0.0$' templates/plugin-repo/plugin.yml.example || fail "submission plugin.yml example must use stable SemVer"
grep -q '"releaseVersion": "1.0.0"' templates/plugin-repo/store.json.example || fail "submission store example must declare a release version"
grep -q '"sha256"' templates/plugin-repo/store.json.example || fail "submission store example must declare a JAR checksum"
grep -q '"pluginYml": "plugin.yml"' templates/plugin-repo/submission.json.example || fail "submission manifest example must reference plugin.yml"
grep -q '"license": "LICENSE"' templates/plugin-repo/submission.json.example || fail "submission manifest example must reference its license"
grep -q 'MR' templates/plugin-repo/README.md || fail "template README must restrict third-party authors to merge requests"
grep -q '普通 MR CI' templates/plugin-repo/README.md || fail "template README must state the ordinary MR credential boundary"
grep -q 'protected tag' templates/plugin-repo/README.md || fail "template README must state the protected release boundary"
grep -q '版本不可覆盖' templates/plugin-repo/README.md || fail "template README must state immutable versions"
grep -q 'SPI' templates/plugin-repo/README.md || fail "template README must prohibit embedding SPI"

# Submission examples describe materials only: no credentials or upload client may be added.
if grep -Eq 'NEXUS_(USERNAME|PASSWORD)|curl[[:space:]]|wget[[:space:]]|mvn[[:space:]].*deploy|pnpm[[:space:]].*publish' \
  templates/plugin-repo/plugin.yml.example \
  templates/plugin-repo/store.json.example \
  templates/plugin-repo/submission.json.example \
  templates/plugin-repo/LICENSE; then
  fail "third-party submission examples must not contain Nexus credentials or upload commands"
fi

echo "[verify-plugin-repo-template] checking template docs for local absolute paths"
if grep -R -n -E '(/D:/code|D:/code|D:\\code\\|C:/Users/|C:\\Users\\|\.jdks/)' templates/plugin-repo/README.md templates/plugin-repo/docs >/dev/null 2>&1; then
  fail "template documentation must not contain local machine absolute paths"
fi

echo "[verify-plugin-repo-template] OK"
