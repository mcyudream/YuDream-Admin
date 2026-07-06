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

# 默认使用 GitLab 容器仓库，可在 .env 中覆盖
export CI_REGISTRY_IMAGE="${CI_REGISTRY_IMAGE:-registry.yudream.online/yudream/yudreamadmin}"
export TAG="${TAG:-latest}"

# 创建插件目录（用于单独挂载更新插件 JAR）
mkdir -p plugins

cmd="${1:-up}"

case "$cmd" in
  up)
    docker compose pull
    docker compose up -d
    ;;
  pull)
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
