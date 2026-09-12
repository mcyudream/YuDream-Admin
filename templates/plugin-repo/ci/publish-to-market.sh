#!/usr/bin/env bash
# 将构建好的插件 JAR 发布到自托管 YuDream 插件市场源。
#
# 环境变量：
#   YUDREAM_MARKET_URL            YuDream 宿主根地址（HTTPS），如 https://yudream.example.com
#   YUDREAM_MARKET_API_KEY        具备 platform:plugin-market-source:upload 权限的 API Key（yda_ 开头）
#   YUDREAM_MARKET_RELEASE_NOTES  可选，发布说明（单行；契约禁止控制字符）
#   YUDREAM_MARKET_CATEGORY       可选，内置分类清单值（如 Minecraft、主题与皮肤）
#   YUDREAM_MARKET_TAGS           可选，逗号分隔标签（最多 10 个，单个 ≤24 字符）
#   YUDREAM_MARKET_METADATA       可选，JSON：{"publisher":{"id","name","url","verified"},"license","compatibility":{"host","spi","frontendSdk"},"category","tags"}
#
# 用法：publish-to-market.sh dist/plugins/<artifactId>-<version>.jar
# 说明：元数据由服务端从 JAR 内 plugin.yml 自动解析；{code}@{version} 不可覆盖，
#       重复发布同一版本会返回 400；服务端开启 reviewRequired 时返回 status=PENDING。
set -euo pipefail

MARKET_JAR="${1:-}"
if [ -z "$MARKET_JAR" ] || [ ! -f "$MARKET_JAR" ]; then
  echo "用法: publish-to-market.sh <plugin.jar>" >&2
  exit 1
fi
: "${YUDREAM_MARKET_URL:?YUDREAM_MARKET_URL is required}"
: "${YUDREAM_MARKET_API_KEY:?YUDREAM_MARKET_API_KEY is required}"

publish_args=(
  --fail-with-body
  -sS
  -X POST
  "$YUDREAM_MARKET_URL/api/platform/plugin-market-source/publications"
  -H "X-API-Key: $YUDREAM_MARKET_API_KEY"
  -F "file=@$MARKET_JAR"
)
if [ -n "${YUDREAM_MARKET_RELEASE_NOTES:-}" ]; then
  publish_args+=(-F "releaseNotes=$YUDREAM_MARKET_RELEASE_NOTES")
fi
if [ -n "${YUDREAM_MARKET_CATEGORY:-}" ]; then
  publish_args+=(-F "category=$YUDREAM_MARKET_CATEGORY")
fi
if [ -n "${YUDREAM_MARKET_TAGS:-}" ]; then
  publish_args+=(-F "tags=$YUDREAM_MARKET_TAGS")
fi
if [ -n "${YUDREAM_MARKET_METADATA:-}" ]; then
  publish_args+=(-F "metadata=$YUDREAM_MARKET_METADATA")
fi

response=$(curl "${publish_args[@]}")
echo "$response"
