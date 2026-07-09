#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

git status --short -- \
  .gitlab-ci.yml \
  .npmrc.example \
  settings.xml.example \
  README.md \
  pom.xml \
  ci \
  docs \
  yudream-frontend/package.json \
  yudream-frontend/pnpm-workspace.yaml \
  yudream-frontend/pnpm-lock.yaml

git status --short -- yudream-frontend/packages yudream-plugins | \
  grep -E '^[ MADRCU?!]{2} (yudream-frontend/packages/plugin-|yudream-plugins/yudream-plugin-)' || true
