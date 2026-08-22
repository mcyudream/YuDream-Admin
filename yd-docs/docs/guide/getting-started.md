# 快速启动

## 方式一：Docker Compose 部署（推荐）

应用镜像发布在 `registry.yudream.online/yudream/yudreamadmin`，包括 `backend`、`frontend` 和 `render-server`。三个服务在 Compose 网络中分别以 `backend`、`frontend`、`render-server` 为 hostname；前端镜像内置 nginx，将 `/api/` 反代到 `http://backend:8080/api/`。下面两套文件都是完整模板，不依赖仓库根目录的其他 Compose 文件。

### 模板 A：使用已有 MongoDB / Redis（无数据库容器）

先在与 `docker-compose.yml` 同一目录创建 `.env`：

```dotenv
TAG=latest
BACKEND_PORT=8080
FRONTEND_PORT=80
RENDER_PORT=3000
MONGO_URI=mongodb://mongo.example.com:27017/yudream
REDIS_HOST=redis.example.com
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DB=0
SNOWFLAKE_DCI=1
SNOWFLAKE_MI=1
MESSAGE_RENDER_BASE_URL=http://render-server:3000
MESSAGE_RENDER_TOKEN=
RENDER_TOKEN=
YUDREAM_MILKY_CREDENTIAL_KEY=
PLATFORM_RABBITMQ_ENABLED=false
PLATFORM_NEO4J_ENABLED=false
PLATFORM_WIKI_ENABLED=false
PLATFORM_AI_ENABLED=false
PLATFORM_AGENT_ENABLED=false
PLATFORM_MILKY_ENABLED=false
PLATFORM_MESSAGE_RENDER_ENABLED=true
```

```yaml
services:
  backend:
    image: registry.yudream.online/yudream/yudreamadmin/backend:${TAG:-latest}
    restart: unless-stopped
    ports:
      - "${BACKEND_PORT:-8080}:8080"
    environment:
      SERVER_PORT: 8080
      MONGO_URI: ${MONGO_URI:?MONGO_URI is required}
      REDIS_HOST: ${REDIS_HOST:?REDIS_HOST is required}
      REDIS_PORT: ${REDIS_PORT:-6379}
      REDIS_PASSWORD: ${REDIS_PASSWORD:-}
      REDIS_DB: ${REDIS_DB:-0}
      SNOWFLAKE_DCI: ${SNOWFLAKE_DCI:-1}
      SNOWFLAKE_MI: ${SNOWFLAKE_MI:-1}
      MESSAGE_RENDER_BASE_URL: ${MESSAGE_RENDER_BASE_URL:-http://render-server:3000}
      MESSAGE_RENDER_TOKEN: ${MESSAGE_RENDER_TOKEN:-}
      YUDREAM_MILKY_CREDENTIAL_KEY: ${YUDREAM_MILKY_CREDENTIAL_KEY:-}
      PLATFORM_RABBITMQ_ENABLED: ${PLATFORM_RABBITMQ_ENABLED:-false}
      PLATFORM_NEO4J_ENABLED: ${PLATFORM_NEO4J_ENABLED:-false}
      PLATFORM_WIKI_ENABLED: ${PLATFORM_WIKI_ENABLED:-false}
      PLATFORM_AI_ENABLED: ${PLATFORM_AI_ENABLED:-false}
      PLATFORM_AGENT_ENABLED: ${PLATFORM_AGENT_ENABLED:-false}
      PLATFORM_MILKY_ENABLED: ${PLATFORM_MILKY_ENABLED:-false}
      PLATFORM_MESSAGE_RENDER_ENABLED: ${PLATFORM_MESSAGE_RENDER_ENABLED:-true}
    networks: [yudream]

  frontend:
    image: registry.yudream.online/yudream/yudreamadmin/frontend:${TAG:-latest}
    restart: unless-stopped
    ports:
      - "${FRONTEND_PORT:-80}:80"
    networks: [yudream]

  render-server:
    image: registry.yudream.online/yudream/yudreamadmin/render-server:${TAG:-latest}
    restart: unless-stopped
    expose: ["3000"]
    ports:
      - "${RENDER_PORT:-3000}:3000"
    environment:
      RENDER_HOST: 0.0.0.0
      RENDER_PORT: 3000
      RENDER_TOKEN: ${RENDER_TOKEN:-${MESSAGE_RENDER_TOKEN:-}}
    healthcheck:
      test: ["CMD", "node", "-e", "fetch('http://127.0.0.1:3000/health').then(r => { if (!r.ok) process.exit(1) })"]
      interval: 10s
      timeout: 5s
      retries: 6
    networks: [yudream]

networks:
  yudream:
    driver: bridge
```

A 中 `MONGO_URI`、`REDIS_HOST` 必填，分别是已有 MongoDB 的完整连接串和 Redis 的可达 hostname；其余变量均可省略并采用表中默认值。`TAG` 控制三枚应用镜像 tag；三个 `*_PORT` 是宿主端口，容器端口固定为 backend `8080`、frontend `80`、render-server `3000`。`MESSAGE_RENDER_BASE_URL` 必须使用 Compose hostname，而不是宿主机 `localhost`。`MESSAGE_RENDER_TOKEN` 是后端配置名，`RENDER_TOKEN` 是渲染容器兼容配置名，若使用 token 应保持一致；当前 render-server 源码只注册 `/health` 和渲染接口，未实现 token 校验，因此生产环境不要发布 render 端口。`SNOWFLAKE_DCI`/`SNOWFLAKE_MI` 是数据中心/机器编号，多实例必须错开。`YUDREAM_MILKY_CREDENTIAL_KEY` 启用 Milky 时才需要，必须是 Base64 编码且解码后为 16/24/32 字节；不启用可留空。`PLATFORM_*_ENABLED` 是平台能力的项目闸门，模板关闭未提供的 RabbitMQ、Neo4j、Wiki、AI、Agent、Milky，消息渲染默认开启。

### 模板 B：Compose 内置 MongoDB / Redis

先创建 `.env`（MongoDB 和 Redis 仅在 Compose 网络内暴露）：

```dotenv
TAG=latest
BACKEND_PORT=8080
FRONTEND_PORT=80
RENDER_PORT=3000
MONGO_URI=mongodb://mongo:27017/yudream
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DB=0
SNOWFLAKE_DCI=1
SNOWFLAKE_MI=1
MESSAGE_RENDER_BASE_URL=http://render-server:3000
MESSAGE_RENDER_TOKEN=
RENDER_TOKEN=
YUDREAM_MILKY_CREDENTIAL_KEY=
PLATFORM_RABBITMQ_ENABLED=false
PLATFORM_NEO4J_ENABLED=false
PLATFORM_WIKI_ENABLED=false
PLATFORM_AI_ENABLED=false
PLATFORM_AGENT_ENABLED=false
PLATFORM_MILKY_ENABLED=false
PLATFORM_MESSAGE_RENDER_ENABLED=true
```

```yaml
services:
  mongo:
    image: docker.io/library/mongo:8.0
    restart: unless-stopped
    command: ["mongod", "--bind_ip_all"]
    volumes: [mongo-data:/data/db]
    healthcheck:
      test: ["CMD-SHELL", "mongosh --quiet --eval 'db.adminCommand({ ping: 1 }).ok' | grep 1"]
      interval: 10s
      timeout: 5s
      retries: 12
    networks: [yudream]

  redis:
    image: docker.io/library/redis:7.4-alpine
    restart: unless-stopped
    volumes: [redis-data:/data]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 12
    networks: [yudream]

  backend:
    image: registry.yudream.online/yudream/yudreamadmin/backend:${TAG:-latest}
    restart: unless-stopped
    ports: ["${BACKEND_PORT:-8080}:8080"]
    depends_on:
      mongo: { condition: service_healthy }
      redis: { condition: service_healthy }
    environment:
      SERVER_PORT: 8080
      MONGO_URI: mongodb://mongo:27017/yudream
      REDIS_HOST: redis
      REDIS_PORT: 6379
      REDIS_PASSWORD: ${REDIS_PASSWORD:-}
      REDIS_DB: ${REDIS_DB:-0}
      SNOWFLAKE_DCI: ${SNOWFLAKE_DCI:-1}
      SNOWFLAKE_MI: ${SNOWFLAKE_MI:-1}
      MESSAGE_RENDER_BASE_URL: ${MESSAGE_RENDER_BASE_URL:-http://render-server:3000}
      MESSAGE_RENDER_TOKEN: ${MESSAGE_RENDER_TOKEN:-}
      YUDREAM_MILKY_CREDENTIAL_KEY: ${YUDREAM_MILKY_CREDENTIAL_KEY:-}
      PLATFORM_RABBITMQ_ENABLED: ${PLATFORM_RABBITMQ_ENABLED:-false}
      PLATFORM_NEO4J_ENABLED: ${PLATFORM_NEO4J_ENABLED:-false}
      PLATFORM_WIKI_ENABLED: ${PLATFORM_WIKI_ENABLED:-false}
      PLATFORM_AI_ENABLED: ${PLATFORM_AI_ENABLED:-false}
      PLATFORM_AGENT_ENABLED: ${PLATFORM_AGENT_ENABLED:-false}
      PLATFORM_MILKY_ENABLED: ${PLATFORM_MILKY_ENABLED:-false}
      PLATFORM_MESSAGE_RENDER_ENABLED: ${PLATFORM_MESSAGE_RENDER_ENABLED:-true}
    networks: [yudream]

  frontend:
    image: registry.yudream.online/yudream/yudreamadmin/frontend:${TAG:-latest}
    restart: unless-stopped
    ports: ["${FRONTEND_PORT:-80}:80"]
    networks: [yudream]

  render-server:
    image: registry.yudream.online/yudream/yudreamadmin/render-server:${TAG:-latest}
    restart: unless-stopped
    expose: ["3000"]
    ports: ["${RENDER_PORT:-3000}:3000"]
    environment:
      RENDER_HOST: 0.0.0.0
      RENDER_PORT: 3000
      RENDER_TOKEN: ${RENDER_TOKEN:-${MESSAGE_RENDER_TOKEN:-}}
    healthcheck:
      test: ["CMD", "node", "-e", "fetch('http://127.0.0.1:3000/health').then(r => { if (!r.ok) process.exit(1) })"]
      interval: 10s
      timeout: 5s
      retries: 6
    networks: [yudream]

volumes:
  mongo-data:
  redis-data:

networks:
  yudream:
    driver: bridge
```

B 中除 `TAG`、宿主端口、雪花编号、能力开关和渲染配置外，Mongo/Redis 连接已固定为服务 hostname `mongo`/`redis`，因此不能把 `MONGO_URI` 改成 `localhost`。MongoDB 与 Redis 没有宿主端口映射，数据分别持久化到 `mongo-data`、`redis-data`。

### 模板变量逐项说明

下表覆盖两套模板实际写入 Compose 的变量；未列出的 `application.yml` 变量不会因为模板而自动注入。模板 A 的 `MONGO_URI`、`REDIS_HOST` 必填；模板 B 使用固定的 `mongo`、`redis`，`.env` 中同名示例仅用于说明连接形式。

| 变量 | 必填 | 作用 | 默认值/示例 |
|---|---:|---|---|
| `TAG` | 否 | 三个 YuDream 镜像的版本 tag。 | `latest` |
| `BACKEND_PORT` | 否 | 宿主访问 backend 的端口；容器内固定 `8080`。 | `8080` |
| `FRONTEND_PORT` | 否 | 宿主访问 frontend nginx 的端口；容器内固定 `80`。 | `80` |
| `RENDER_PORT` | 否 | 宿主访问 render-server 的端口；生产建议不映射。 | `3000` |
| `MONGO_URI` | A 是 | Spring Data MongoDB 完整连接串。 | A：`mongodb://mongo.example.com:27017/yudream`；B：`mongodb://mongo:27017/yudream` |
| `REDIS_HOST` | A 是 | Redis hostname；B 固定为 Compose 服务 `redis`。 | A：`redis.example.com`；B：`redis` |
| `REDIS_PORT` | 否 | Redis 端口。 | `6379` |
| `REDIS_PASSWORD` | 否 | Redis 密码；模板 B 默认无密码。 | 空 |
| `REDIS_DB` | 否 | Redis database 编号。 | `0` |
| `SNOWFLAKE_DCI` | 否 | 雪花 ID 数据中心编号。 | `1` |
| `SNOWFLAKE_MI` | 否 | 雪花 ID 机器编号；多实例必须错开。 | `1` |
| `MESSAGE_RENDER_BASE_URL` | 否 | backend 调用渲染服务的 URL，必须使用服务 hostname。 | `http://render-server:3000` |
| `MESSAGE_RENDER_TOKEN` | 否 | backend 的渲染 token 配置。 | 空；与 `RENDER_TOKEN` 保持一致 |
| `RENDER_TOKEN` | 否 | 注入 render-server 的兼容 token 配置。 | 空；取 `MESSAGE_RENDER_TOKEN` |
| `YUDREAM_MILKY_CREDENTIAL_KEY` | Milky 启用时 | Milky 凭据 AES-GCM 密钥，Base64 解码后为 16/24/32 字节。 | 空；可用 `openssl rand -base64 32` 生成 |
| `PLATFORM_RABBITMQ_ENABLED` | 否 | RabbitMQ 项目闸门；模板未提供 RabbitMQ。 | `false` |
| `PLATFORM_NEO4J_ENABLED` | 否 | Neo4j 项目闸门；模板未提供 Neo4j。 | `false` |
| `PLATFORM_WIKI_ENABLED` | 否 | Wiki 项目闸门；模板未提供 Neo4j。 | `false` |
| `PLATFORM_AI_ENABLED` | 否 | AI 项目闸门。 | `false` |
| `PLATFORM_AGENT_ENABLED` | 否 | Agent 项目闸门。 | `false` |
| `PLATFORM_MILKY_ENABLED` | 否 | Milky 项目闸门。 | `false` |
| `PLATFORM_MESSAGE_RENDER_ENABLED` | 否 | 消息渲染项目闸门；要使用 render-server 必须开启。 | `true` |

render-server 当前源码实际提供 `GET /health` 和 `/v1/render/*`，没有读取或校验 `RENDER_TOKEN`；文档仍同时保留两个变量名，是为了与 backend 配置和现有 Compose 兼容。它只是内网服务，生产环境应删除 `ports`，只保留 `expose`。删除 render-server 时将 `PLATFORM_MESSAGE_RENDER_ENABLED=false`，并移除 backend 的 `MESSAGE_RENDER_BASE_URL`/token 配置即可；其余服务仍按模板运行。

## 完整环境变量参考

上面的两套 Compose 只提供**模板最小运行变量**：模板 A 必须补充 `MONGO_URI`、`REDIS_HOST`，模板 B 将它们固定为 Compose 服务名；其余变量用于雪花 ID、渲染服务和能力开关。下面是当前后端 `application.yml`、基础设施插件配置和 render-server 配置支持的**完整可选配置**。未出现在两套模板中的变量不会自动注入，只有启用对应能力或功能时才需要设置。

Spring 占位符中的 `${VAR:default}` 表示变量缺省时使用 `default`；`${VAR}` 没有默认值，Spring 在解析配置时要求变量存在。特别是 `MAIL_USERNAME`、`MAIL_PASSWORD` 和 `YUDREAM_PLUGIN_SECRET_KEY` 缺失时，应用会在启动配置/Bean 初始化阶段失败，而不是“使用空值继续运行”。`MAIL_FROM` 未设置时继承 `MAIL_USERNAME`，因此邮件配置仍要求提供用户名和密码。

### 后端基础、邮件与缓存

| 变量 | 默认值 | 必填 | 作用 | 适用条件 | 安全与部署注意事项 |
|---|---|---:|---|---|---|
| `MONGO_URI` | 无 | 是 | MongoDB 连接串。 | 所有后端启动 | 缺失会导致 Spring 配置解析失败；使用账号密码时不要提交到仓库。 |
| `REDIS_HOST` | `localhost` | 否 | Redis 主机名。 | 使用 Redis 的功能 | Compose 内应使用 `redis` 或外部可达 hostname，不能用宿主机 `localhost`。 |
| `REDIS_PORT` | `6379` | 否 | Redis 端口。 | Redis | 仅暴露内网或通过安全网络访问。 |
| `REDIS_PASSWORD` | 空 | 否 | Redis 密码。 | Redis 开启认证时 | 生产环境建议启用认证并通过 Secret 注入。 |
| `REDIS_DB` | `0` | 否 | Redis database 编号。 | Redis | 多应用共用实例时规划隔离。 |
| `REDIS_TIMEOUT` | `2000ms` | 否 | Redis 操作超时时间。 | Redis | 网络较慢时可调整，过大可能拖慢请求。 |
| `REDIS_POOL_MAX_ACTIVE` | `8` | 否 | 连接池最大活动连接数。 | Redis 高并发部署 | 结合 Redis 和应用实例容量调整。 |
| `REDIS_POOL_MAX_IDLE` | `8` | 否 | 连接池最大空闲连接数。 | Redis | 不宜盲目增大。 |
| `REDIS_POOL_MIN_IDLE` | `0` | 否 | 连接池最小空闲连接数。 | Redis | 需要预热连接时再提高。 |
| `MAIL_HOST` | `smtp.qq.com` | 否 | SMTP 主机。 | 邮件验证、通知等邮件功能 | 改为实际邮件服务商；必须使用可信 SMTP。 |
| `MAIL_PORT` | `465` | 否 | SMTP 端口。 | 邮件功能 | 与 SSL/STARTTLS 模式匹配。 |
| `MAIL_USERNAME` | 无 | 是（邮件 Bean） | SMTP 用户名及默认发件人来源。 | 应用启用邮件自动配置时 | 缺失会导致启动失败；不要写入镜像或日志。 |
| `MAIL_PASSWORD` | 无 | 是（邮件 Bean） | SMTP 密码或授权码。 | 应用启用邮件自动配置时 | 缺失会导致启动失败；使用 Secret，QQ 邮箱通常使用授权码。 |
| `MAIL_SSL_ENABLE` | `true` | 否 | 启用 SMTP SSL。 | 邮件功能 | 465 端口通常保持开启。 |
| `MAIL_STARTTLS_ENABLE` | `false` | 否 | 启用 SMTP STARTTLS。 | 使用 STARTTLS 的邮件服务 | 与服务商端口和 SSL 设置二选一匹配。 |
| `MAIL_FROM` | `${MAIL_USERNAME}` | 否 | 邮件显示的发件人地址。 | 邮件功能 | 必须是 SMTP 服务允许的地址，未设置时继承用户名。 |
| `SNOWFLAKE_DCI` | `1` | 否 | 雪花 ID 数据中心编号。 | 所有生成雪花 ID 的实例 | 多数据中心/实例必须规划且避免重复。 |
| `SNOWFLAKE_MI` | `1` | 否 | 雪花 ID 机器编号。 | 所有生成雪花 ID 的实例 | 多实例必须错开，否则可能产生 ID 冲突。 |
| `CACHE_KEY_PREFIX` | `yudream` | 否 | 缓存 key 前缀。 | 缓存 | 多套环境共用 Redis 时设置不同前缀。 |
| `CACHE_ENABLED` | `true` | 否 | 开启应用缓存。 | 需要缓存时 | 关闭会改变性能，不等于关闭 Redis 连接。 |
| `CACHE_DEFAULT_NULL_EXPIRE` | `60` | 否 | 空值缓存过期时间。 | 缓存 | 按数据变更频率调整。 |
| `CACHE_L1_ENABLED` | `false` | 否 | 开启进程内 L1 缓存。 | 缓存优化 | 多实例场景注意数据一致性。 |
| `CACHE_L1_MAX_SIZE` | `10000` | 否 | L1 缓存最大条目数。 | L1 缓存 | 过大将增加内存占用。 |
| `CACHE_L1_EXPIRE` | `60` | 否 | L1 写入后过期时间。 | L1 缓存 | 秒数，需接受短暂旧数据。 |
| `CACHE_METRICS_ENABLED` | `true` | 否 | 开启缓存指标。 | 缓存监控 | 增加少量指标开销。 |
| `CACHE_METRICS_AGGREGATE` | `true` | 否 | 按前缀聚合缓存指标。 | 缓存指标 | 前缀规划应避免敏感信息进入指标名。 |

### 地址、日志与系统种子

| 变量 | 默认值 | 必填 | 作用 | 适用条件 | 安全与部署注意事项 |
|---|---|---:|---|---|---|
| `APP_BASE_URL` | `http://localhost:8080` | 否 | 生成邮件验证链接的后端地址。 | 邮件验证 | 生产环境必须设置为外部 HTTPS 地址。 |
| `APP_WEB_URL` | `${APP_BASE_URL}`（其默认为 `http://localhost:9000`） | 否 | Web 前端地址。 | 邮件链接、跳转 | 设置为用户实际访问地址，避免生成 localhost 链接。 |
| `WEB_LOG_ENABLED` | `true` | 否 | 开启 Web 请求日志。 | 请求审计/排障 | 生产按合规要求控制日志量，避免记录凭据。 |
| `WEB_LOG_PREFIX` | `YuDreamAdmin` | 否 | Web 日志前缀。 | Web 请求日志 | 仅用于分类。 |
| `SYSTEM_SEED_MENU_SYNC_MODE` | `MISSING_ONLY` | 否 | 系统菜单种子同步模式。 | 启动菜单初始化 | 修改前确认是否会覆盖既有菜单权限。 |
| `SYSTEM_LOG_DOCKER_ENABLED` | `false` | 否 | 开启 Docker 容器日志采集。 | 需要采集容器日志时 | 必须同时提供 Docker socket 或 CLI 权限；不要无审慎地暴露 socket。 |
| `SYSTEM_LOG_DOCKER_CONTAINERS` | 空 | 否 | 要采集的容器列表。 | Docker 日志采集 | 仅列出必要容器。 |
| `SYSTEM_LOG_DOCKER_TAIL` | `200` | 否 | 每个容器初始读取的日志行数。 | Docker 日志采集 | 过大增加启动和内存开销。 |
| `SYSTEM_LOG_DOCKER_TRANSPORT` | `auto` | 否 | 日志传输方式：`auto`、`cli` 或 `socket`。 | Docker 日志采集 | `socket` 需要挂载 `/var/run/docker.sock`；`cli` 需要 Docker CLI。 |
| `SYSTEM_LOG_DOCKER_SOCKET` | `/var/run/docker.sock` | 否 | Docker API socket 路径。 | `socket` 或自动探测 | socket 等同高权限入口，仅挂载可信运行环境。 |

### 平台能力与连接参数

以下 `PLATFORM_*_ENABLED` 是项目闸门；值为 `false` 时对应 provider/端点不应加载。能力还可能需要在管理端完成应用闸门启用，并提供下表中的连接参数。

| 变量 | 默认值 | 必填 | 作用 | 适用条件 | 安全与部署注意事项 |
|---|---|---:|---|---|---|
| `PLATFORM_API_DOCS_ENABLED` | `true` | 否 | API 文档能力开关。 | Swagger/OpenAPI | 生产按需开放并限制访问。 |
| `PLATFORM_CMS_ENABLED` | `true` | 否 | CMS 能力开关。 | CMS | 关闭会隐藏/停用相关能力。 |
| `PLATFORM_WIKI_ENABLED` | `true` | 否 | Wiki 能力开关。 | Wiki | 使用 Wiki 知识图谱时还需 Neo4j 参数。 |
| `PLATFORM_FORM_ENABLED` | `true` | 否 | 表单能力开关。 | 动态表单 | 关闭后不注册对应能力。 |
| `PLATFORM_DOCUMENT_TEMPLATE_ENABLED` | `true` | 否 | 文档模板能力开关。 | 文档生成 | 处理模板文件时限制文件来源和大小。 |
| `PLATFORM_INTEGRATION_ENABLED` | `true` | 否 | 外部集成能力开关。 | HTTP/Python 等集成 | 外部请求和进程执行应限制权限。 |
| `PLATFORM_SSE_ENABLED` | `true` | 否 | SSE 能力开关。 | SSE | 按代理和超时策略配置。 |
| `PLATFORM_WEBSOCKET_ENABLED` | `true` | 否 | WebSocket 能力开关。 | WebSocket | 反向代理必须正确转发 Upgrade。 |
| `PLATFORM_RABBITMQ_ENABLED` | `true` | 否 | RabbitMQ 项目闸门。 | RabbitMQ | 未部署 RabbitMQ 时设为 `false`。 |
| `PLATFORM_NEO4J_ENABLED` | `true` | 否 | Neo4j 项目闸门。 | Neo4j/Wiki 图谱 | 未部署 Neo4j 时设为 `false`。 |
| `PLATFORM_AI_ENABLED` | `true` | 否 | AI 能力项目闸门。 | AI | 还需在应用层启用并配置 provider。 |
| `PLATFORM_AGENT_ENABLED` | `true` | 否 | Agent 能力项目闸门。 | Agent | 依赖的 AI/工具不可用时不能启用。 |
| `PLATFORM_DATAVIZ_ENABLED` | `true` | 否 | 数据可视化能力开关。 | 数据可视化 | 按需关闭。 |
| `PLATFORM_MILKY_ENABLED` | `true` | 否 | Milky 能力项目闸门。 | Milky | 启用并实际保存凭据时必须配置密钥。 |
| `PLATFORM_MESSAGE_RENDER_ENABLED` | `true` | 否 | 消息渲染能力项目闸门。 | backend 调用 render-server | Compose 最小模板保持开启；不部署渲染服务时设为 `false`。 |
| `PLATFORM_AI_CONNECT_TIMEOUT` | `30s` | 否 | AI provider 建连超时。 | AI | 按网络环境调整。 |
| `PLATFORM_AI_READ_TIMEOUT` | `30m` | 否 | AI 普通读取超时。 | AI | 长超时会占用连接和线程资源。 |
| `PLATFORM_AI_SSE_TIMEOUT` | `30m` | 否 | AI SSE 读取超时。 | AI 流式请求 | 需与反向代理超时一致。 |
| `PLATFORM_WIKI_NEO4J_URI` | `bolt://localhost:7687` | 否 | Wiki Neo4j URI。 | Wiki + Neo4j | Compose 网络应使用 Neo4j 服务名，生产不要暴露数据库。 |
| `PLATFORM_WIKI_NEO4J_USERNAME` | `neo4j` | 否 | Wiki Neo4j 用户名。 | Wiki + Neo4j | 生产更换默认账号。 |
| `PLATFORM_WIKI_NEO4J_PASSWORD` | `${NEO4J_PASSWORD}`，再回退 `yudream123456` | 否 | Wiki Neo4j 密码。 | Wiki + Neo4j | 生产必须显式设置强密码；不要依赖示例默认值。 |
| `PLATFORM_WIKI_NEO4J_DATABASE` | `neo4j` | 否 | Wiki Neo4j database。 | Wiki + Neo4j | 与实际数据库名称一致。 |
| `NEO4J_PASSWORD` | `yudream123456`（仅作为上项回退） | 否 | 为 Wiki Neo4j 密码提供兼容回退。 | 未设置 `PLATFORM_WIKI_NEO4J_PASSWORD` 时 | 生产不要使用该默认密码，应直接设置 `PLATFORM_WIKI_NEO4J_PASSWORD`。 |
| `PUBLIC_WIKI_CHAT_SSE_TIMEOUT` | `3m` | 否 | 公开 Wiki 问答 SSE 超时。 | 公开 Wiki 问答 | 与代理超时协调。 |
| `PLATFORM_CHAT_SSE_TIMEOUT` | `30m` | 否 | 平台聊天 SSE 超时。 | 聊天 | 长连接需配合网关配置。 |
| `PUBLIC_WIKI_CHAT_EXECUTOR_CORE` | `2` | 否 | 公开问答线程池核心线程数。 | 公开 Wiki 问答 | 按 CPU 和并发限制调整。 |
| `PUBLIC_WIKI_CHAT_EXECUTOR_MAX` | `2` | 否 | 公开问答线程池最大线程数。 | 公开 Wiki 问答 | 与限流策略配套，避免资源耗尽。 |
| `PUBLIC_WIKI_CHAT_EXECUTOR_QUEUE` | `0` | 否 | 公开问答线程池队列容量。 | 公开 Wiki 问答 | `0` 表示超限快速拒绝。 |

### 渲染、对象存储与插件

| 变量 | 默认值 | 必填 | 作用 | 适用条件 | 安全与部署注意事项 |
|---|---|---:|---|---|---|
| `MESSAGE_RENDER_BASE_URL` | `http://localhost:3000` | 否 | backend 调用 render-server 的地址。 | 消息渲染 | 容器内使用 `http://render-server:3000`；不要使用宿主 localhost。 |
| `MESSAGE_RENDER_TOKEN` | 空 | 否 | backend 的渲染 token 配置。 | 兼容现有部署 | 当前 render-server 不校验此 token；服务只放内网。 |
| `MESSAGE_RENDER_TIMEOUT` | `30s` | 否 | 渲染请求超时。 | 消息渲染 | 过大可能积压请求。 |
| `MESSAGE_RENDER_MAX_RESPONSE_SIZE` | `16MB` | 否 | 渲染响应最大大小。 | 消息渲染 | 防止异常响应占用内存。 |
| `S3_ENDPOINT` | `http://localhost:9000` | 否 | S3 兼容对象存储地址。 | 文件/对象存储 | 生产使用 HTTPS 或可信内网地址。 |
| `S3_ACCESS_KEY` | `rustfsadmin` | 否 | 对象存储访问密钥。 | 对象存储 | 示例默认值仅适合本地开发，生产必须更换并用 Secret。 |
| `S3_SECRET_KEY` | `rustfsadmin` | 否 | 对象存储私钥。 | 对象存储 | 生产必须更换，不得提交或打印。 |
| `S3_BUCKET` | `yudream-admin` | 否 | 对象存储 bucket。 | 对象存储 | 按环境隔离并配置最小权限。 |
| `S3_REGION` | `us-east-1` | 否 | S3 region。 | 对象存储 | 与服务端配置一致。 |
| `S3_PATH_STYLE_ACCESS` | `true` | 否 | 使用 path-style 访问。 | S3 兼容服务 | 按对象存储兼容性选择。 |
| `YUDREAM_MILKY_CREDENTIAL_KEY` | 空 | Milky 保存凭据时 | Milky 凭据 AES-GCM 密钥。 | Milky | 必须是 Base64 且解码后为 16/24/32 字节；缺失时应用可启动，但加解密操作会失败。 |
| `YUDREAM_PLUGIN_SECRET_KEY` | 无 | 是（插件密钥存储） | 插件 SecretStore 的 AES-GCM 密钥。 | 插件 secret 存储/插件框架初始化 | 缺失、非 Base64 或解码后不是 32 字节会使相关 Bean 初始化失败；使用 `openssl rand -base64 32` 生成并安全注入。 |
| `PLATFORM_PLUGIN_HOST_VERSION` | `1.0.0` | 否 | 插件宿主兼容版本。 | 插件兼容性检查 | 只在确认插件契约兼容时修改。 |
| `PLATFORM_PLUGIN_SPI_VERSION` | `2.6.0` | 否 | 插件 SPI 兼容版本。 | 插件兼容性检查 | 必须与实际宿主 SPI 契约匹配。 |
| `PLATFORM_PLUGIN_FRONTEND_SDK_VERSION` | `1.0.1` | 否 | 插件前端 SDK 兼容版本。 | 插件前端兼容性检查 | 与宿主实际 SDK 版本保持一致。 |
| `PLATFORM_PLUGIN_STORE_ROOT_URL` | `https://nexus.yudream.online/repository/plugin-store-releases/index.json` | 否 | 插件商店索引地址。 | 在线插件商店 | 仅信任受控 HTTPS 地址。 |
| `PLATFORM_PLUGIN_STORE_CONNECT_TIMEOUT_MILLIS` | `5000` | 否 | 商店连接超时（毫秒）。 | 在线插件商店 | 网络不稳定时谨慎增大。 |
| `PLATFORM_PLUGIN_STORE_REQUEST_TIMEOUT_MILLIS` | `5000` | 否 | 商店请求超时（毫秒）。 | 在线插件商店 | 避免启动或管理请求长时间阻塞。 |
| `PLATFORM_PLUGIN_STORE_MAX_RESPONSE_BYTES` | `1048576` | 否 | 商店索引最大响应字节数。 | 在线插件商店 | 维持限制以降低内存和供应链风险。 |
| `YUDREAM_PLATFORM_PLUGIN_ENABLED` | `true` | 否 | 插件运行时总开关（`PluginProperties`）。 | 加载 JAR 插件 | 生产只加载可信插件。 |
| `YUDREAM_PLATFORM_PLUGIN_DIRECTORIES` | `plugins` | 否 | 插件目录列表（`PluginProperties`）。 | 加载 JAR 插件 | 目录应为受控路径，避免加载未知 JAR。 |
| `YUDREAM_PLATFORM_PLUGIN_STORE_MAX_JAR_BYTES` | `104857600` | 否 | 插件商店 JAR 最大大小。 | 在线安装插件 | 保留大小限制，防止异常包消耗磁盘。 |
| `YUDREAM_PLATFORM_PLUGIN_DEV_MODE_ENABLED` | 未设置时按运行形态自动判断 | 否 | 插件源码开发模式开关。 | 本地开发/热重载 | 生产显式设为 `false`；不要让生产加载源码目录。 |
| `YUDREAM_PLATFORM_PLUGIN_DEV_MODE_STORE_FILE` | 空（回退 `plugins/dev-projects.json`） | 否 | 开发项目清单文件。 | 插件开发模式 | 仅使用可信本地路径。 |
| `YUDREAM_PLATFORM_PLUGIN_DEV_MODE_POLL_INTERVAL_MS` | `1000` | 否 | 开发模式轮询间隔。 | 插件开发模式 | 仅影响开发监视器。 |
| `YUDREAM_PLATFORM_PLUGIN_DEV_MODE_DEBOUNCE_MS` | `800` | 否 | 文件变更防抖时间。 | 插件开发模式 | 仅影响开发监视器。 |
| `YUDREAM_PLATFORM_PLUGIN_DEV_MODE_COMPILE_TIMEOUT_SECONDS` | `180` | 否 | 插件自动编译超时秒数。 | 插件开发模式 | 编译命令具有本机执行权限，只在受控开发环境开启。 |
| `YUDREAM_PLATFORM_PLUGIN_DEV_MODE_PROJECTS` | 空列表 | 否 | 静态登记的开发项目列表。 | 插件开发模式 | 列表及其 `code`、`path` 等字段建议写入 YAML；不要让生产环境从不可信路径加载源码。 |
| `YUDREAM_PLATFORM_PLUGIN_DEV_MODE_PROJECTS_0_CODE` / `_PATH` | 无 | 否 | 第一个开发项目的插件 code 和源码路径；其余项目将索引改为 `1`、`2`。 | 插件开发模式 | 仅限本地开发；`path` 会触发本机编译和类加载。 |
| `YUDREAM_PLATFORM_PLUGIN_DEV_MODE_PROJECTS_0_FRONTEND_DIST` | 空（按布局推导） | 否 | 第一个开发项目的前端产物目录。 | 插件开发模式 | 使用受控目录；复杂列表配置优先使用配置文件。 |
| `YUDREAM_PLATFORM_PLUGIN_DEV_MODE_PROJECTS_0_AUTO_COMPILE` | `true` | 否 | 第一个开发项目是否自动执行编译。 | 插件开发模式 | 关闭可避免文件变更触发编译；编译命令只应指向可信项目。 |
| `YUDREAM_PLATFORM_PLUGIN_DEV_MODE_PROJECTS_0_COMPILE_COMMAND` | `mvn -q compile -DskipTests` | 否 | 第一个开发项目的编译命令。 | 插件开发模式 | 具有本机命令执行权限，生产禁止开启开发模式。 |
| `YUDREAM_PLATFORM_SEMANTIC_MEMORY_NEO4J_URI` | `bolt://localhost:7687` | 否 | 插件语义记忆默认 Neo4j URI。 | 使用插件语义记忆且未被能力配置覆盖时 | 容器内使用服务名，生产不要暴露数据库。 |
| `YUDREAM_PLATFORM_SEMANTIC_MEMORY_NEO4J_USERNAME` | `neo4j` | 否 | 插件语义记忆默认 Neo4j 用户名。 | 插件语义记忆 | 生产更换默认账号。 |
| `YUDREAM_PLATFORM_SEMANTIC_MEMORY_NEO4J_PASSWORD` | 空 | 否 | 插件语义记忆默认 Neo4j 密码。 | 插件语义记忆 | 通过 Secret 注入，不要写入配置仓库。 |
| `YUDREAM_PLATFORM_SEMANTIC_MEMORY_NEO4J_DATABASE` | `neo4j` | 否 | 插件语义记忆默认 Neo4j database。 | 插件语义记忆 | 与实际 Neo4j 数据库名称一致。 |

插件 `@ConfigurationProperties` 使用 Spring relaxed binding，因此上表中 `YUDREAM_PLATFORM_PLUGIN_*` 对应 `yudream.platform.plugin.*`；开发项目列表等复杂对象建议写入配置文件而不是环境变量。`PluginSemanticMemoryFrameworkService` 同样通过 Spring 占位符读取 `YUDREAM_PLATFORM_SEMANTIC_MEMORY_NEO4J_*`。插件密钥和 Milky 密钥不是模板最小运行变量：未启用相应功能时可以不提供，但生产启用前必须先注入并验证格式。

### render-server 专用变量

这些变量由 `yudream-render-server/src/config.ts` 直接读取，和 backend 的 `MESSAGE_RENDER_*` 不是同一组配置：

| 变量 | 默认值 | 必填 | 作用 | 适用条件 | 安全与部署注意事项 |
|---|---|---:|---|---|---|
| `RENDER_HOST` | `127.0.0.1` | 否 | render-server 监听地址。 | 独立运行 render-server | Compose 必须设为 `0.0.0.0` 才能被 backend 访问。 |
| `RENDER_PORT` | `3000` | 否 | render-server 监听端口。 | render-server | 容器端口固定时不要与 Compose 映射混淆。 |
| `RENDER_MAX_QUEUE` | `32` | 否 | 最大渲染排队数。 | render-server 高并发 | 过大增加内存和等待时间。 |
| `RENDER_MAX_CONCURRENT` | `2` | 否 | 最大并行渲染数。 | render-server | 按 CPU、浏览器资源和实例数调整。 |

render-server 当前源码没有读取 `RENDER_TOKEN`，也没有 token 校验；Compose 中保留它只是兼容现有模板。生产部署应删除 render-server 的宿主 `ports`，仅通过 Compose 内网 `expose` 访问。

两套模板的启动、停止和日志命令：

```bash
docker compose config
docker compose pull
docker compose up -d
docker compose ps
docker compose logs -f backend
docker compose logs -f frontend render-server
docker compose down                 # 停止并删除容器，保留数据卷
docker compose down -v              # 同时删除 MongoDB/Redis 数据卷（谨慎）
```

健康检查：`docker compose ps` 应显示 render-server、MongoDB、Redis 为 `healthy`（A 的外部 MongoDB/Redis 由其自身平台检查）；浏览器访问 `${FRONTEND_PORT:-80}` 映射出的地址，或执行 `curl http://localhost:${RENDER_PORT:-3000}/health` 检查渲染服务。backend 当前配置未提供专用 actuator 健康端点，以 `docker compose logs backend` 无启动异常并通过前端 `/api/` 请求验证。

首次登录：启动后访问前端地址（默认 `http://localhost`），进入登录页使用项目初始化数据提供的管理员账号；若是全新数据库且没有初始化账号，请先按项目的种子/初始化流程创建管理员，不要把 MongoDB 端口暴露到公网。

`docker-compose.platform.yml` 另提供 RabbitMQ（profile `mq`）和 Neo4j（profile `graph`）；只有同时提供对应连接配置并打开相应 `PLATFORM_*_ENABLED` 时才启用。
## 方式二：本地源码运行

### 环境要求

| 组件 | 版本 |
|---|---|
| JDK | 21 |
| Maven | 3.9+（Windows 下需显式设置 JDK 21，否则会误用 JDK 17 报 `invalid target release: 21`） |
| Node.js | 22.22+ / 24.15+ |
| pnpm | 11.9+ |
| MongoDB / Redis | 本地或远程实例 |

### 后端

1. 配置 `yudream-bootstrap/src/main/resources/application.yml` 要点：
   - `MONGO_URI`（必填）、Redis 连接、雪花 ID（`SNOWFLAKE_DCI` / `SNOWFLAKE_MI`）、S3 存储 `S3_*`
2. 运行主类 `online.yudream.base.bootstrap.YuDreamApplication`，端口 **8080**。

命令行编译验证：

```bash
mvn -pl yudream-bootstrap -am -DskipTests compile
```

### 前端

```bash
pnpm --dir yudream-frontend dev
```

核心应用为 `@fantastic-admin/core-arco-design-vue`。类型检查：

```bash
pnpm --dir yudream-frontend --filter @fantastic-admin/core-arco-design-vue run test:typecheck
```

### 渲染服务（可选）

```bash
cd yudream-render-server
pnpm install
pnpm exec playwright install chromium
pnpm dev          # 开发模式（tsx watch），默认端口 3000
# 生产：pnpm build && pnpm start
```

## 下一步

- 了解系统内部设计 → [系统架构](/guide/architecture)
- 开始写插件 → [创建你的第一个插件](/plugin/getting-started)
