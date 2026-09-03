#!/usr/bin/env bash
# 把 kkFileView 镜像发布到平台私有仓库，与 backend/frontend 同一命名空间：
#   registry.yudream.online/yudream/yudreamadmin/kkfileview:<version>
#
# 用法：
#   sh docker/kkfileview/push.sh [版本]        # 默认 5.0.2，拉取官方镜像转推（首选）
#   MODE=build sh docker/kkfileview/push.sh    # 备选：用本目录 Dockerfile 从源码构建
#
# 前置条件：先 docker login registry.yudream.online（GitLab 容器仓库）。
# 凭据只进本机 docker 配置，禁止写入仓库、脚本或文档。
set -euo pipefail

VERSION="${1:-5.0.2}"
REGISTRY="${REGISTRY:-registry.yudream.online/yudream/yudreamadmin}"
TARGET="${REGISTRY}/kkfileview:${VERSION}"
MODE="${MODE:-retag}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ "$MODE" = "build" ]; then
  echo "==> 从源码构建 kkFileView ${VERSION}"
  docker build --build-arg "KKFILEVIEW_VERSION=${VERSION}" -t "$TARGET" "$SCRIPT_DIR"
else
  SOURCE_MIRROR="swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/keking/kkfileview:${VERSION}"
  SOURCE_OFFICIAL="keking/kkfileview:${VERSION}"
  # 国内网络优先走 ddn-k8s 镜像站，失败后回退 Docker Hub 官方地址
  if docker pull "$SOURCE_MIRROR"; then
    SOURCE="$SOURCE_MIRROR"
  else
    echo "==> 镜像站拉取失败，回退 Docker Hub"
    docker pull "$SOURCE_OFFICIAL"
    SOURCE="$SOURCE_OFFICIAL"
  fi
  docker tag "$SOURCE" "$TARGET"
fi

docker push "$TARGET"
echo "==> 已推送 $TARGET"
