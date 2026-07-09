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
grep -q 'sh ci/publish-plugin-jars.sh' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must publish plugin jars"
grep -q 'sh ci/verify-published-plugin-jars.sh' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must verify published plugin jars"
grep -q 'PACKAGE_MAVEN_REPO' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI package job must use a dedicated clean Maven local repository"
grep -Eq '^[[:space:]]*-[[:space:]]+yudream-frontend/packages/plugin-\*/package\.json$' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI must restrict frontend job discovery to plugin packages"
grep -q 'pnpm -r --filter=@yudream/plugin-\* run build' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI frontend build must explicitly filter @yudream/plugin-* packages"
grep -Eq '^[[:space:]]*-[[:space:]]+yudream-frontend/packages/plugin-\*/dist/$' templates/plugin-repo/.gitlab-ci.yml.example || fail "template CI artifacts must stay limited to plugin package dist outputs"
if grep -Eq '^[[:space:]]*-[[:space:]]+yudream-frontend/packages/\*/package\.json$' templates/plugin-repo/.gitlab-ci.yml.example; then
  fail "template CI must not use yudream-frontend/packages/*/package.json"
fi

echo "[verify-plugin-repo-template] checking workspace boundary example"
grep -Eq '^[[:space:]]*-[[:space:]]+packages/plugin-\*$' templates/plugin-repo/pnpm-workspace.yaml.example || fail "template pnpm workspace must restrict packages to packages/plugin-*"
if grep -Eq '^[[:space:]]*-[[:space:]]+packages/\*$' templates/plugin-repo/pnpm-workspace.yaml.example; then
  fail "template pnpm workspace must not use packages/*"
fi

echo "[verify-plugin-repo-template] checking npm registry example"
grep -q '^registry=https://registry.npmjs.org/$' templates/plugin-repo/.npmrc.example || fail "template .npmrc must default to npmjs registry"
grep -q '^@yudream:registry=https://registry.npmjs.org/$' templates/plugin-repo/.npmrc.example || fail "template .npmrc must default @yudream scope to npmjs registry"

echo "[verify-plugin-repo-template] checking template docs for local absolute paths"
if grep -R -n -E '(/D:/code|D:/code|D:\\code\\|C:/Users/|C:\\Users\\|\.jdks/)' templates/plugin-repo/README.md templates/plugin-repo/docs >/dev/null 2>&1; then
  fail "template documentation must not contain local machine absolute paths"
fi

echo "[verify-plugin-repo-template] OK"
