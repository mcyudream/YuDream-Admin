#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

fail() {
  echo "[verify-core-plugin-decoupling] $1" >&2
  exit 1
}

OFFICIAL_PLUGINS="
yudream-plugin-alipay
yudream-plugin-authlib-injector
yudream-plugin-minecraft-activity-proof
yudream-plugin-minecraft-server
yudream-plugin-project-progress
yudream-plugin-student-info
yudream-plugin-wallet
yudream-plugin-yudream-skin
"

echo "[verify-core-plugin-decoupling] checking Maven reactor"
for plugin in $OFFICIAL_PLUGINS; do
  if grep -q "<module>yudream-plugins/$plugin</module>" pom.xml; then
    fail "root pom.xml must not include migrated official plugin module: $plugin"
  fi
done

echo "[verify-core-plugin-decoupling] checking migrated plugin source directories"
for plugin in $OFFICIAL_PLUGINS; do
  case "$plugin" in
    yudream-plugin-alipay)
      frontend_code='yudream-alipay'
      ;;
    yudream-plugin-student-info)
      frontend_code='yudream-student-info'
      ;;
    yudream-plugin-wallet)
      frontend_code='yudream-wallet'
      ;;
    yudream-plugin-yudream-skin)
      frontend_code='yudream-skin'
      ;;
    *)
      frontend_code=$(printf '%s' "$plugin" | sed 's/^yudream-plugin-//')
      ;;
  esac
  if [ -d "yudream-plugins/$plugin" ]; then
    fail "migrated official backend plugin directory must not remain in core repo: yudream-plugins/$plugin"
  fi
  if [ -d "yudream-frontend/packages/plugin-$frontend_code" ]; then
    fail "migrated official frontend plugin directory must not remain in core repo: yudream-frontend/packages/plugin-$frontend_code"
  fi
done

echo "[verify-core-plugin-decoupling] checking frontend workspace"
if grep -Eq '^[[:space:]]*-[[:space:]]+packages/\*$' yudream-frontend/pnpm-workspace.yaml; then
  fail "yudream-frontend/pnpm-workspace.yaml must not use packages/* after plugin repo split"
fi

if grep -E '^[[:space:]]*-[[:space:]]+packages/plugin-' yudream-frontend/pnpm-workspace.yaml | grep -v 'plugin-sdk' >/dev/null 2>&1; then
  fail "yudream-frontend/pnpm-workspace.yaml must not include business plugin packages"
fi

echo "[verify-core-plugin-decoupling] checking frontend lockfile importers"
for frontend_code in alipay authlib-injector minecraft-activity-proof minecraft-server project-progress yudream-skin yudream-student-info yudream-wallet; do
  if grep -q "^  packages/plugin-$frontend_code:" yudream-frontend/pnpm-lock.yaml; then
    fail "core pnpm lockfile must not keep migrated business plugin importer: packages/plugin-$frontend_code"
  fi
done

echo "[verify-core-plugin-decoupling] checking host frontend runtime"
HOST_RUNTIME_FILES="
yudream-frontend/apps/core-arco-design-vue/src/views/platform/plugin/runtime-page.vue
yudream-frontend/apps/core-arco-design-vue/src/views/dashboard/DashboardRemotePluginCard.vue
"

for file in $HOST_RUNTIME_FILES; do
  if grep -q 'packages/plugin-' "$file"; then
    fail "host frontend still references local plugin source in $file"
  fi
  if grep -q 'import.meta.glob<YuDreamPluginFrontendModule' "$file"; then
    fail "host frontend still uses local plugin source glob in $file"
  fi
done

echo "[verify-core-plugin-decoupling] checking host source tree"
if grep -R -n 'packages/plugin-' yudream-frontend/apps/core-arco-design-vue/src >/dev/null 2>&1; then
  fail "core host source tree still contains local business plugin source references"
fi

echo "[verify-core-plugin-decoupling] OK"
