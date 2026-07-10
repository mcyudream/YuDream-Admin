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
require_file "ci/verify-plugin-repo-independence.sh"
require_file "ci/verify-plugin-maven-boundary.sh"
require_file "ci/verify-core-maven-registry.sh"
require_file "ci/verify-core-npm-contracts.sh"
require_file "ci/verify-plugin-jar-assets.sh"
require_file "ci/publish-plugin-jars.sh"
require_file "ci/verify-published-plugin-jars.sh"

echo "[verify-plugin-publish-pipeline] checking stage layout"
require_pattern '^[[:space:]]*-[[:space:]]\+validate$' "plugin CI must keep validate stage"
require_pattern '^[[:space:]]*-[[:space:]]\+build-frontend$' "plugin CI must keep build-frontend stage"
require_pattern '^[[:space:]]*-[[:space:]]\+package-plugin$' "plugin CI must keep package-plugin stage"
require_pattern '^[[:space:]]*-[[:space:]]\+publish-plugin$' "plugin CI must keep publish-plugin stage"
require_pattern '^[[:space:]]*-[[:space:]]\+verify-publish$' "plugin CI must keep verify-publish stage"

echo "[verify-plugin-publish-pipeline] checking validation jobs"
require_pattern '^validate:independence:$' "plugin CI must validate repository independence"
require_pattern '^validate:plugin-maven-boundary:$' "plugin CI must validate plugin Maven boundary"
require_pattern '^validate:core-maven-registry:$' "plugin CI must validate core Maven registry access"
require_pattern '^validate:core-npm-contracts:$' "plugin CI must validate core npm contracts"
require_pattern '^validate:docs:$' "plugin CI must validate documentation independence"
require_pattern '^validate:publish-pipeline:$' "plugin CI must validate its own publish pipeline shape"
require_pattern 'sh ci/verify-plugin-publish-pipeline.sh' "plugin CI must call ci/verify-plugin-publish-pipeline.sh"

echo "[verify-plugin-publish-pipeline] checking package/publish/verify chain"
require_pattern '^package:plugins:$' "plugin CI must keep package:plugins job"
require_pattern 'PACKAGE_MAVEN_REPO' "plugin CI package job must use a dedicated clean Maven local repository"
require_pattern 'sh ci/verify-plugin-jar-assets.sh' "plugin CI package job must verify plugin jar assets"
require_pattern 'copy_final_plugin_jars "\$PWD" "\$PWD/dist/plugins"' "plugin CI package job must flatten final plugin jars into dist/plugins"
require_pattern '^[[:space:]]*-[[:space:]]\+dist/plugins/\*\.jar$' "plugin CI package artifacts must expose flat dist/plugins jars"
require_pattern '^publish:plugin-jars:$' "plugin CI must keep publish:plugin-jars job"
require_pattern 'sh ci/publish-plugin-jars.sh' "plugin CI publish job must upload plugin jars"
require_pattern '^verify:published-plugin-jars:$' "plugin CI must keep verify:published-plugin-jars job"
require_pattern 'sh ci/verify-published-plugin-jars.sh' "plugin CI must re-read published plugin jars after upload"

echo "[verify-plugin-publish-pipeline] checking Nexus-only package routing"
require_pattern 'NEXUS_MAVEN_PUBLIC_URL' "plugin CI must pull Maven artifacts through Nexus maven-public"
require_pattern 'NEXUS_MAVEN_RELEASES_URL' "plugin CI must publish plugin artifacts to Nexus maven-releases"
require_pattern 'NEXUS_NPM_PUBLIC_URL' "plugin CI must pull npm artifacts through Nexus npm-public"
grep -q 'NEXUS_USERNAME' ci/publish-plugin-jars.sh || fail "plugin publishing must require a Nexus username"
grep -q 'NEXUS_PASSWORD' ci/publish-plugin-jars.sh || fail "plugin publishing must require a Nexus password"
if grep -Eq 'NEXUS_(USERNAME|PASSWORD)' ci/verify-core-maven-registry.sh ci/verify-core-npm-contracts.sh ci/verify-published-plugin-jars.sh; then
  fail "plugin read and verification paths must not require protected publish credentials"
fi
if grep -q '<mirrorOf>\*</mirrorOf>' .gitlab-ci.yml settings.xml.example ci/*.sh; then
  fail "plugin builds must not route third-party Maven dependencies through Nexus"
fi
grep -q 'https://maven.aliyun.com/repository/public' .gitlab-ci.yml || fail "plugin builds must resolve third-party Maven dependencies from Aliyun"
grep -q 'external:\*,!nexus-public,!nexus-releases,!nexus-snapshots' .gitlab-ci.yml || fail "plugin builds must exclude YuDream Nexus repositories from the Aliyun mirror"
grep -q 'remoteRepositories=nexus-public' ci/verify-core-maven-registry.sh || fail "SPI verification must explicitly resolve YuDream artifacts from Nexus"
grep -q 'remoteRepositories=nexus-public' ci/verify-published-plugin-jars.sh || fail "plugin JAR and catalog verification must explicitly resolve YuDream artifacts from Nexus"
grep -q 'maven-deploy-plugin.*deploy-file' ci/publish-plugin-jars.sh || fail "plugin publish script must deploy Maven artifacts"
grep -q 'yudream\.plugin\.spi\.version' ci/verify-core-maven-registry.sh || fail "core Maven verification must derive the SPI version from the plugin root POM"
if grep -q 'YUDREAM_PLUGIN_SPI_VERSION:-1.0-SNAPSHOT' ci/verify-core-maven-registry.sh; then
  fail "core Maven verification must not default to a hard-coded SPI snapshot"
fi
reject_pattern 'packages/generic' "plugin publishing must not use GitLab Generic Package Registry"
reject_pattern 'JOB-TOKEN:' "plugin publishing must not authenticate to a registry with GitLab job tokens"

echo "[verify-plugin-publish-pipeline] checking publish rules"
require_pattern '\$CI_COMMIT_TAG =~ /\^v/' "plugin CI publish/verify jobs must stay tag-gated"

echo "[verify-plugin-publish-pipeline] OK"
