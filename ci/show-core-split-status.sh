#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

git status --short -- \
  .gitignore \
  .gitlab-ci.yml \
  .npmrc.npmjs.example \
  .codex/skills/yudream-contract-release \
  .codex/skills/yudream-ddd-architecture/references/knowledge.json \
  README.md \
  ci \
  docs/plugin-system \
  docs/repository-split \
  pom.xml \
  skills/yudream-contract-release \
  templates/plugin-repo \
  yudream-plugins/yudream-plugin-spi/pom.xml \
  yudream-frontend/packages/plugin-sdk \
  yudream-frontend/packages/components/package.json \
  yudream-frontend/pnpm-workspace.yaml \
  yudream-frontend/pnpm-lock.yaml \
  yudream-frontend/apps/core-arco-design-vue/package.json \
  yudream-frontend/apps/core-arco-design-vue/src/plugins/sdk/index.ts \
  yudream-frontend/apps/core-arco-design-vue/src/views/dashboard/DashboardRemotePluginCard.vue \
  yudream-frontend/apps/core-arco-design-vue/src/views/platform/plugin/runtime-page.vue \
  yudream-frontend/apps/core-arco-design-vue/vite.config.ts \
  yudream-frontend/apps/core-arco-design-vue/vite/plugins.ts

git status --short -- yudream-frontend/packages yudream-plugins | \
  grep -E '^[ MADRCU?!]{2} (yudream-frontend/packages/plugin-|yudream-plugins/yudream-plugin-)' | \
  grep -v '^.. yudream-frontend/packages/plugin-sdk/' | \
  grep -v '^.. yudream-frontend/packages/plugin-sdk$' | \
  grep -v '^.. yudream-plugins/yudream-plugin-spi/' | \
  grep -v '^.. yudream-plugins/yudream-plugin-spi$' || true
