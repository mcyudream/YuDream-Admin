#!/usr/bin/env bash
# 服务端初始化 / 手动更新脚本
# 用法：
#   ./deploy.sh              # 首次部署或手动拉最新镜像并启动
#   ./deploy.sh pull         # 只拉取最新镜像并 up -d（watchtower 会自动做，这里供手动触发）

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 从 .env 读取镜像仓库等配置（可选）
if [ -f .env ]; then
  set -a
  # shellcheck source=/dev/null
  source .env
  set +a
fi

# 默认使用 Harbor 镜像仓库，可在 .env 中覆盖
export CI_REGISTRY_IMAGE="${CI_REGISTRY_IMAGE:-registry.yudream.online/yda}"
export TAG="${TAG:-latest}"

# Harbor 项目为私有时需要凭据：.env 里提供 DOCKER_REGISTRY_USER / DOCKER_REGISTRY_PASSWORD 即自动登录，
# 留空则按匿名拉取（yda / library 默认公开，无需登录）。
docker_login_if_needed() {
  if [ -z "${DOCKER_REGISTRY_USER:-}" ] && [ -z "${DOCKER_REGISTRY_PASSWORD:-}" ]; then
    echo "==> 未配置 DOCKER_REGISTRY_USER/DOCKER_REGISTRY_PASSWORD，按匿名拉取镜像"
    return 0
  fi
  if [ -z "${DOCKER_REGISTRY_USER:-}" ] || [ -z "${DOCKER_REGISTRY_PASSWORD:-}" ]; then
    echo "!! DOCKER_REGISTRY_USER 与 DOCKER_REGISTRY_PASSWORD 必须同时配置" >&2
    exit 1
  fi
  REGISTRY_SERVER="${DOCKER_REGISTRY:-$(printf '%s' "$CI_REGISTRY_IMAGE" | cut -d/ -f1)}"
  printf '%s' "$DOCKER_REGISTRY_PASSWORD" | docker login "$REGISTRY_SERVER" -u "$DOCKER_REGISTRY_USER" --password-stdin
}

# 创建插件目录（单独挂载更新插件 JAR）与引导配置目录（安装向导落盘 config/yudream-bootstrap.properties）
mkdir -p plugins config market-source

cmd="${1:-up}"

case "$cmd" in
  up)
    docker_login_if_needed
    docker compose pull
    docker compose up -d
    ;;
  pull)
    docker_login_if_needed
    docker compose pull
    docker compose up -d
    ;;
  restart)
    docker compose restart
    ;;
  logs)
    docker compose logs -f
    ;;
  *)
    echo "Usage: $0 {up|pull|restart|logs}"
    exit 1
    ;;
esac

echo ""
echo "Deployment status:"
docker compose ps
