#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

fail() {
  echo "[verify-core-contract-publish-pipeline] $1" >&2
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

echo "[verify-core-contract-publish-pipeline] checking required verification scripts"
require_file "ci/verify-plugin-spi-registry.sh"
require_file "ci/verify-published-npm-contracts.sh"
require_file "ci/verify-core-remote-release-evidence.sh"
require_file "ci/maven-settings-nexus.xml"
[ ! -e ".npmrc.npmjs.example" ] || fail "legacy npmjs publish configuration must be removed"

echo "[verify-core-contract-publish-pipeline] checking stage layout"
require_pattern '^[[:space:]]*-[[:space:]]\+publish-packages$' "core CI must keep publish-packages stage"
require_pattern '^[[:space:]]*-[[:space:]]\+verify-packages$' "core CI must keep verify-packages stage"

echo "[verify-core-contract-publish-pipeline] checking Maven publish/verify jobs"
require_pattern '^publish:maven-plugin-spi:$' "core CI must publish yudream-plugin-spi"
require_pattern '^verify:maven-plugin-spi:$' "core CI must verify published yudream-plugin-spi"
require_pattern 'sh ci/verify-plugin-spi-registry.sh' "core CI must call ci/verify-plugin-spi-registry.sh after Maven publish"
grep -q 'SPI_POM' ci/verify-plugin-spi-registry.sh \
  || fail "SPI registry verification must resolve the version from the SPI POM"
if grep -q 'help:evaluate' ci/verify-plugin-spi-registry.sh; then
  fail "SPI registry verification must not resolve its version through an unconfigured Maven repository"
fi
if grep -q '1.0-SNAPSHOT' ci/verify-plugin-spi-registry.sh; then
  fail "SPI registry verification must not default to a hard-coded snapshot version"
fi
require_pattern 'maven-settings-nexus.xml' "core CI must deploy Maven contracts with Nexus settings"
require_pattern 'NEXUS_MAVEN_PUBLIC_URL' "core CI must configure the Nexus maven-public read endpoint"
if grep -q '<mirrorOf>' ci/maven-settings-nexus.xml ci/maven-settings.xml; then
  fail "core Maven settings must preserve explicit Aliyun-to-Nexus repository ordering"
fi
grep -q 'https://maven.aliyun.com/repository/public' ci/maven-settings-nexus.xml \
  || fail "core Maven settings must resolve third-party dependencies from Aliyun"
grep -q '<id>nexus-plugin</id>' ci/maven-settings-nexus.xml \
  || fail "core Maven plugin resolution must fall back from Aliyun to Nexus"
grep -q '<id>nexus-plugin</id>' ci/verify-plugin-spi-registry.sh \
  || fail "SPI verification Maven plugins must fall back from Aliyun to Nexus"
if grep -Eq 'maven-dependency-plugin[^[:space:]]*:get|dependency:get|remoteRepositories=' .gitlab-ci.yml; then
  fail "core CI must not prefetch Maven artifacts outside the configured repository order"
fi
if grep -R -Eq 'gitlab-maven|gitlab\.yudream\.online/api/v4/projects|CI_JOB_TOKEN|CORE_PACKAGE_(USER|TOKEN)|packages/(maven|npm)' \
  .gitlab-ci.yml ci/maven-settings-nexus.xml ci/maven-settings.xml ci/verify-plugin-spi-registry.sh; then
  fail "core Maven publish and verification paths must not use GitLab Package Registry"
fi
grep -q 'remoteRepositories=nexus-public' ci/verify-plugin-spi-registry.sh \
  || fail "SPI verification must explicitly resolve the YuDream artifact from Nexus"

echo "[verify-core-contract-publish-pipeline] checking Nexus npm publish/verify jobs"
require_pattern '^publish:npm-plugin-sdk:$' "core CI must publish @yudream/plugin-sdk to Nexus"
require_pattern '^publish:npm-components:$' "core CI must publish @yudream/components to Nexus"
require_pattern '^verify:npm-contracts:$' "core CI must verify Nexus npm contracts"
require_pattern 'VERIFY_NPM_REGISTRY="\$NEXUS_NPM_PUBLIC_URL" sh ci/verify-published-npm-contracts.sh' "core CI must re-install npm contracts from Nexus npm-public"
require_pattern 'pnpm publish --no-git-checks --registry "\$NEXUS_NPM_PUBLIC_URL"' "core CI must publish npm contracts to Nexus npm-public"
require_pattern 'NEXUS_USERNAME' "core CI must use the shared Nexus username"
require_pattern 'NEXUS_PASSWORD' "core CI must use the shared Nexus password"

echo "[verify-core-contract-publish-pipeline] checking publish rules"
require_pattern '\$CI_COMMIT_TAG =~ /\^v/' "core CI publish/verify jobs must stay tag-gated"

if grep -Eq 'packages/(maven|npm)|publish:npmjs-|verify:(gitlab-npm|npmjs)|GITLAB_NPM_PUBLISH_ENABLED|NPMJS_PUBLISH_ENABLED|NPM_TOKEN' .gitlab-ci.yml; then
  fail "core CI must not publish contracts to GitLab Package Registry or npmjs"
fi

grep -q 'verify:npm-contracts' ci/verify-core-remote-release-evidence.sh \
  || fail "remote release evidence must require the Nexus npm verification job"
if grep -Eq 'EXPECT_(GITLAB_NPM|NPMJS)_PUBLISH|publish:npmjs-|verify:(gitlab-npm|npmjs)' ci/verify-core-remote-release-evidence.sh; then
  fail "remote release evidence must not require legacy GitLab npm or npmjs jobs"
fi

echo "[verify-core-contract-publish-pipeline] OK"
