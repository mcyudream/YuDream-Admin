#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

fail() {
  echo "[verify-plugin-publish-pipeline] $1" >&2
  exit 1
}

require_file() {
  file=$1
  [ -f "$file" ] || fail "missing required file: $file"
}

require_pattern() {
  pattern=$1
  message=$2
  grep -q "$pattern" .gitlab-ci.yml || fail "$message"
}

reject_pattern() {
  pattern=$1
  message=$2
  if grep -q "$pattern" .gitlab-ci.yml ci/publish-plugin-jars.sh ci/verify-published-plugin-jars.sh; then
    fail "$message"
  fi
}

echo "[verify-plugin-publish-pipeline] checking required verification scripts"
for file in \
  ci/verify-plugin-repo-independence.sh ci/verify-plugin-maven-boundary.sh \
  ci/verify-core-maven-registry.sh ci/verify-core-npm-contracts.sh \
  ci/verify-plugin-jar-assets.sh ci/verify-plugin-release-selection.sh \
  ci/publish-plugin-jars.sh ci/verify-published-plugin-jars.sh release/plugins.txt; do
  require_file "$file"
done

echo "[verify-plugin-publish-pipeline] checking stage layout"
for stage in validate build-frontend package-plugin publish-plugin verify-publish; do
  require_pattern "^[[:space:]]*-[[:space:]]\\+$stage$" "plugin CI must keep $stage stage"
done

echo "[verify-plugin-publish-pipeline] checking validation jobs"
for job in independence plugin-maven-boundary core-maven-registry core-npm-contracts docs publish-pipeline release-selection; do
  require_pattern "^validate:$job:$" "plugin CI must validate $job"
done
require_pattern 'sh ci/verify-plugin-release-selection.sh' "plugin CI must validate explicit release selection"
require_pattern 'sh ci/verify-plugin-publish-pipeline.sh' "plugin CI must call ci/verify-plugin-publish-pipeline.sh"

echo "[verify-plugin-publish-pipeline] checking explicit release packaging"
require_pattern '^package:plugins:$' "plugin CI must keep package:plugins job"
require_pattern 'PACKAGE_MAVEN_REPO' "plugin CI package job must use a dedicated clean Maven local repository"
require_pattern 'selected_plugin_modules_csv' "tag package must resolve canonical selected modules"
require_pattern 'clean package -pl "\$release_modules" -am' "tag package must build selected modules with Maven -pl and -am"
require_pattern 'PLUGIN_RELEASE_ONLY="\${CI_COMMIT_TAG:+1}" sh ci/verify-plugin-jar-assets.sh' "tag package must verify only selected jars"
require_pattern 'PLUGIN_RELEASE_ONLY="\${CI_COMMIT_TAG:+1}" copy_final_plugin_jars' "tag package must stage only selected jars"
require_pattern '^[[:space:]]*-[[:space:]]\+dist/plugins/\*\.jar$' "plugin CI package artifacts must expose flat dist/plugins jars"
require_pattern '^publish:plugin-jars:$' "plugin CI must keep publish:plugin-jars job"
require_pattern 'PLUGIN_RELEASE_ONLY=1 sh ci/publish-plugin-jars.sh' "tag publishing must publish only selected jars"
require_pattern '^verify:published-plugin-jars:$' "plugin CI must keep verify:published-plugin-jars job"
require_pattern 'PLUGIN_RELEASE_ONLY=1 sh ci/verify-published-plugin-jars.sh' "tag verification must verify only selected jars"

echo "[verify-plugin-publish-pipeline] checking Nexus-only package routing"
require_pattern 'NEXUS_MAVEN_PUBLIC_URL' "plugin CI must pull Maven artifacts through Nexus maven-public"
require_pattern 'NEXUS_MAVEN_RELEASES_URL' "plugin CI must publish plugin artifacts to Nexus maven-releases"
require_pattern 'NEXUS_NPM_PUBLIC_URL' "plugin CI must pull npm artifacts through Nexus npm-public"
grep -q 'NEXUS_USERNAME' ci/publish-plugin-jars.sh || fail "plugin publishing must require a Nexus username"
grep -q 'NEXUS_PASSWORD' ci/publish-plugin-jars.sh || fail "plugin publishing must require a Nexus password"
if grep -Eq 'NEXUS_(USERNAME|PASSWORD)' ci/verify-core-maven-registry.sh ci/verify-core-npm-contracts.sh ci/verify-published-plugin-jars.sh; then
  fail "plugin read and verification paths must not require protected publish credentials"
fi
if grep -q '<mirrorOf>' .gitlab-ci.yml settings.xml.example; then
  fail "plugin builds must preserve explicit Aliyun-to-Nexus repository ordering"
fi
grep -q 'https://maven.aliyun.com/repository/public' .gitlab-ci.yml || fail "plugin builds must resolve third-party Maven dependencies from Aliyun"
grep -q '<id>nexus-plugin</id>' settings.xml.example || fail "plugin Maven plugins must fall back from Aliyun to Nexus"
grep -q '<id>nexus-plugin</id>' .gitlab-ci.yml || fail "plugin CI Maven plugins must fall back from Aliyun to Nexus"
for script in ci/publish-plugin-jars.sh ci/verify-core-maven-registry.sh ci/verify-published-plugin-jars.sh; do
  grep -q '<id>nexus-plugin</id>' "$script" || fail "$script Maven plugins must fall back from Aliyun to Nexus"
done
grep -Fq '<url>${env.NEXUS_MAVEN_PUBLIC_URL}</url>' ci/publish-plugin-jars.sh || fail "plugin publish settings must pass the Nexus plugin fallback URL through Maven environment interpolation"
if grep -Eq 'maven-dependency-plugin[^[:space:]]*:get|dependency:get|remoteRepositories=' .gitlab-ci.yml; then
  fail "plugin CI must not prefetch Maven artifacts outside the configured repository order"
fi
grep -q 'remoteRepositories=nexus-public' ci/verify-core-maven-registry.sh || fail "SPI verification must explicitly resolve YuDream artifacts from Nexus"
grep -q 'remoteRepositories=nexus-public' ci/verify-published-plugin-jars.sh || fail "plugin JAR and catalog verification must explicitly resolve YuDream artifacts from Nexus"
grep -q 'maven-deploy-plugin.*deploy-file' ci/publish-plugin-jars.sh || fail "plugin publish script must deploy Maven artifacts"
grep -q 'yudream\.plugin\.spi\.version' ci/verify-core-maven-registry.sh || fail "core Maven verification must derive the SPI version from the plugin root POM"
if grep -q 'YUDREAM_PLUGIN_SPI_VERSION:-1.0-SNAPSHOT' ci/verify-core-maven-registry.sh; then
  fail "core Maven verification must not default to a hard-coded SPI snapshot"
fi
reject_pattern 'packages/generic' "plugin publishing must not use GitLab Generic Package Registry"
reject_pattern 'JOB-TOKEN:' "plugin publishing must not authenticate to a registry with GitLab job tokens"
if grep -R -Eq 'gitlab-maven|gitlab\.yudream\.online/api/v4/projects|CI_JOB_TOKEN|CORE_PACKAGE_(USER|TOKEN)|packages/(maven|npm)' \
  .gitlab-ci.yml .npmrc.example settings.xml.example ci/publish-plugin-jars.sh \
  ci/verify-core-maven-registry.sh ci/verify-core-npm-contracts.sh ci/verify-published-plugin-jars.sh; then
  fail "plugin package routing must not use GitLab Package Registry"
fi

echo "[verify-plugin-publish-pipeline] checking publish rules"
require_pattern '\$CI_COMMIT_TAG =~ /\^v/' "plugin CI publish/verify jobs must stay tag-gated"

echo "[verify-plugin-publish-pipeline] OK"
