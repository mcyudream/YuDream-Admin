# YuDream Admin

<p align="center">
  <strong>面向可扩展业务场景的现代化管理平台</strong>
</p>

<p align="center">
  <a href="#快速开始">快速开始</a> ·
  <a href="#核心能力">核心能力</a> ·
  <a href="#插件开发">插件开发</a> ·
  <a href="#参与贡献">参与贡献</a>
</p>

<p align="center">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-22c55e?style=flat-square">
  <img alt="Java" src="https://img.shields.io/badge/Java-21-437291?style=flat-square&logo=openjdk&logoColor=white">
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?style=flat-square&logo=springboot&logoColor=white">
  <img alt="Vue" src="https://img.shields.io/badge/Vue-3-42B883?style=flat-square&logo=vuedotjs&logoColor=white">
  <img alt="pnpm" src="https://img.shields.io/badge/pnpm-11.9-F69220?style=flat-square&logo=pnpm&logoColor=white">
  <img alt="Docker" src="https://img.shields.io/badge/Docker-ready-2496ED?style=flat-square&logo=docker&logoColor=white">
</p>

YuDream Admin 是一个以 Java 21、Spring Boot 3 和 Vue 3 构建的管理平台。它提供用户与权限、内容管理、知识库、集成编排、可视化数据、AI Agent 等平台能力，并通过插件运行时将业务功能与平台核心解耦。

项目既可以作为一套完整的后台系统使用，也可以作为团队构建独立业务插件的宿主平台。

## 核心能力

| 方向 | 能力 |
| --- | --- |
| 系统管理 | 用户、角色、部门、菜单、权限、在线用户与安全配置 |
| 内容与站点 | CMS、可视化页面编辑、发布流程、公开站点与 SEO 元数据 |
| 知识与智能 | 知识库检索、文档解析、AI Provider 管理、Agent 应用与可视化工作流 |
| 集成与自动化 | HTTP、Python 运行时、消息队列、WebSocket、SSE、S3 兼容对象存储 |
| 数据能力 | 数据可视化、图谱检索、Neo4j 与可选的 RAG 扩展 |
| 插件生态 | JAR 热加载、动态菜单和权限、后端扩展点、前端 Remote Entry 模块 |

## 为什么使用它

- **分层清晰**：后端遵循 DDD 分层，领域、应用、基础设施和接口职责明确。
- **按需启用**：平台能力可由项目配置和运行状态共同控制，未启用的能力不会强制依赖外部中间件。
- **插件优先**：业务插件可独立开发、构建和发布；平台核心只维护稳定运行时与契约。
- **完整的前端体验**：Vue 3 管理端支持动态路由、主题、多布局和远程插件页面。
- **面向生产部署**：提供 Docker 镜像、Compose 编排与环境变量模板。

## 架构

```text
                         ┌──────────────────────────┐
                         │      Vue 3 管理端          │
                         │  主应用 + 插件 Remote UI   │
                         └────────────┬─────────────┘
                                      │ HTTP / SSE / WebSocket
┌─────────────────────────────────────▼─────────────────────────────────────┐
│                             YuDream Admin Core                              │
│  interfaces  ->  application  ->  domain  <-  infrastructure               │
│  API、鉴权        用例编排       业务规则       Mongo、Redis、AI、S3 等      │
├───────────────────────────────────────────────────────────────────────────┤
│                     Plugin Runtime / yudream-plugin-spi                    │
│          JAR 生命周期 · 菜单权限 · HTTP 扩展 · 前端 Remote 模块             │
└───────────────────────────────────────────────────────────────────────────┘
                                      │
                  ┌──────────────────┴──────────────────┐
                  │                                     │
        独立业务插件仓库                       外部平台与中间件
  yudream-admin-plugins               MongoDB · Redis · S3 · AI Provider
```

### 仓库结构

```text
yudream-domain/             领域模型、聚合与仓储契约
yudream-application/        应用服务、用例编排与 DTO
yudream-infrastructure/     持久化、外部服务适配与插件运行时
yudream-interfaces/         HTTP 接口、请求响应与接口装配
yudream-bootstrap/          Spring Boot 启动模块
yudream-frontend/           Vue 3 主前端与共享包
yudream-plugins/            插件 SPI 与示例插件
plugins/                    运行时加载的外部插件 JAR 目录
docs/                       平台、插件与部署文档
```

## 快速开始

### 环境要求

- JDK 21
- Maven 3.9+
- Node.js `22.22+`、`24.15+` 或更高兼容版本
- pnpm 11.9+
- MongoDB 和 Redis
- Docker 与 Docker Compose（容器化部署时需要）

### 1. 配置环境变量

复制模板并按环境修改数据库、缓存、邮件、对象存储和可选平台能力配置：

```bash
cp .env.example .env
```

Windows PowerShell：

```powershell
Copy-Item .env.example .env
```

> `.env` 可能包含密钥，请仅在本地或部署环境中保存，不要提交到仓库。

### 2. 安装前端依赖

```bash
cd yudream-frontend
pnpm install
```

### 3. 启动后端

在仓库根目录执行：

```bash
mvn -pl yudream-bootstrap -am spring-boot:run
```

### 4. 启动前端

另开一个终端：

```bash
cd yudream-frontend
pnpm dev
```

默认开发地址由前端启动日志输出。首次运行前，请确认 `.env` 中的 MongoDB 与 Redis 连接配置可用。

### 构建验证

```bash
mvn -pl yudream-bootstrap -am -DskipTests compile
```

```bash
cd yudream-frontend
pnpm build
```

## Docker 部署

仓库根目录提供生产 Compose 编排。准备好 `.env` 后执行：

```bash
docker compose pull
docker compose up -d
```

默认会启动后端、前端、渲染服务与镜像更新监控。MongoDB、Redis、S3 兼容存储等依赖需要按照 `.env` 指向可访问的服务。

可选的 RabbitMQ 与 Neo4j 服务位于 `docker-compose.platform.yml`：

```bash
docker compose -f docker-compose.platform.yml --profile mq up -d
docker compose -f docker-compose.platform.yml --profile graph up -d
```

## 插件开发

插件通过稳定契约与平台交互，而不依赖平台内部模块：

- 后端插件依赖 `online.yudream.base:yudream-plugin-spi`。
- 前端插件使用 `@yudream/plugin-sdk` 与 `@yudream/components`。
- 插件 JAR 使用根目录 `plugin.yml` 描述元数据，并由运行时管理加载、启用与卸载。
- 前端插件通过 `remoteEntry.js` 作为远程模块加载到主前端。

新建插件时，建议从 [插件仓库模板](templates/plugin-repo/README.md) 开始；完整约定见 [插件系统规范](docs/plugin-system/specification.md) 和 [插件开发教程](docs/plugin-system/tutorial.md)。官方业务插件源码位于独立仓库 [yudream-admin-plugins](https://gitlab.yudream.online/yudream/yudream-admin-plugins)。

## 文档

- [插件系统规范](docs/plugin-system/specification.md)
- [插件开发教程](docs/plugin-system/tutorial.md)
- [平台能力说明](docs/platform/)
- [仓库拆分与边界说明](docs/repository-split/README.md)
- [契约发布校验](docs/plugin-system/contract-validation.md)

## 参与贡献

欢迎提交 Issue 和 Pull Request。提交前请确保：

1. 改动只覆盖当前问题，不混入无关格式化或生成文件。
2. 后端变更遵循 `domain -> application -> infrastructure -> interfaces` 的职责边界。
3. 新增或修改插件能力时，使用 SPI、SDK 和公开契约，不直接依赖平台内部实现。
4. 运行与改动相关的测试、类型检查或构建命令。
5. 在 Pull Request 中说明行为变化、配置影响和验证结果。

## License

本项目采用 [MIT License](LICENSE) 开源。
