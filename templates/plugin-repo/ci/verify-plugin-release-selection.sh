#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"
. "$ROOT_DIR/ci/lib/plugin-jar-selection.sh"

fail() {
  echo "[verify-plugin-release-selection] $1" >&2
  exit 1
}

expect_invalid_override() {
  override=$1
  if PLUGIN_RELEASE_MODULES=$override selected_plugin_modules "$ROOT_DIR" >/dev/null 2>&1; then
    fail "invalid PLUGIN_RELEASE_MODULES override was accepted: $override"
  fi
}

validate_plugin_release_list "$ROOT_DIR" || exit 1

expected_count=14
actual_count=$(wc -l < "$ROOT_DIR/release/plugins.txt" | tr -d ' ')
[ "$actual_count" = "$expected_count" ] || fail "release/plugins.txt must contain $expected_count modules"

for module in \
  yudream-plugin-ai-chatbot yudream-plugin-alipay yudream-plugin-authlib-injector \
  yudream-plugin-minecraft-activity-proof yudream-plugin-minecraft-server \
  yudream-plugin-project-progress yudream-plugin-qq-binding yudream-plugin-qqbot-automation \
  yudream-plugin-student-info yudream-plugin-wallet yudream-plugin-web-card \
  yudream-plugin-world-map yudream-plugin-yudream-launcher yudream-plugin-yudream-skin; do
  grep -Fx "$module" "$ROOT_DIR/release/plugins.txt" >/dev/null || fail "missing canonical module: $module"
done

expect_invalid_override ''
expect_invalid_override 'yudream-plugin-ai-chatbot,,yudream-plugin-wallet'
expect_invalid_override 'yudream-plugin-ai-chatbot, yudream-plugin-wallet,'
expect_invalid_override 'yudream-plugin-ai-chatbot yudream-plugin-ai-chatbot'
expect_invalid_override 'not-a-plugin'

PLUGIN_RELEASE_MODULES='yudream-plugin-wallet, yudream-plugin-ai-chatbot' selected_plugin_modules "$ROOT_DIR" | cmp -s - - <<'EOF' \
  || fail "valid PLUGIN_RELEASE_MODULES override was not preserved"
yudream-plugin-wallet
yudream-plugin-ai-chatbot
EOF

PACKAGE_VERSION="${PLUGIN_PACKAGE_VERSION:-${CI_COMMIT_TAG:-}}"
PACKAGE_VERSION=${PACKAGE_VERSION#v}
if [ -z "$PACKAGE_VERSION" ]; then
  echo "[verify-plugin-release-selection] OK (list-only)"
  exit 0
fi

JAR_LIST=$(mktemp "${TMPDIR:-/tmp}/yudream-plugin-jars-XXXXXX.txt")
trap 'rm -f "$JAR_LIST"' EXIT INT TERM
PLUGIN_RELEASE_ONLY=1 write_final_plugin_jars "$ROOT_DIR" "$JAR_LIST" \
  || fail "no selected plugin jars found for version verification"

while IFS= read -r jar_path; do
  plugin_version=$(unzip -p "$jar_path" plugin.yml 2>/dev/null | sed -n 's/^version:[[:space:]]*//p' | head -n 1 | tr -d '\r' | tr -d '"' | tr -d "'")
  [ -n "$plugin_version" ] || fail "selected jar has no root plugin.yml version: $jar_path"
  [ "$plugin_version" = "$PACKAGE_VERSION" ] \
    || fail "selected jar plugin.yml version $plugin_version does not match $PACKAGE_VERSION: $jar_path"
done < "$JAR_LIST"

echo "[verify-plugin-release-selection] OK"
