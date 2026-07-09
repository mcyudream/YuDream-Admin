# YuDream Admin Core

<p align="center">面向插件化后台平台与独立业务插件生态的核心仓库</p>

<p align="center">
  <img alt="license" src="https://img.shields.io/badge/license-MIT-67C23A">
  <img alt="java" src="https://img.shields.io/badge/Java-21-437291?logo=openjdk&logoColor=white">
  <img alt="spring-boot" src="https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white">
  <img alt="vue" src="https://img.shields.io/badge/Vue-3-42B883?logo=vuedotjs&logoColor=white">
  <img alt="pnpm" src="https://img.shields.io/badge/pnpm-11.9-F69220?logo=pnpm&logoColor=white">
  <img alt="docker" src="https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white">
</p>

YuDream Admin Core 是 YuDream Admin 的主体仓库，负责提供后端核心能力、主前端宿主、插件运行时，以及面向独立插件仓发布的契约包。

官方业务插件已经默认迁移到独立插件仓维护；当前仓库更聚焦于“平台核心”而不是“业务插件集合”。

## ✨ 功能特性

- ✅ **DDD 核心分层**：内置 `domain / application / infrastructure / interfaces / bootstrap` 分层结构，适合持续演进的大型后台系统。
- ✅ **插件运行时**：支持外部插件 JAR 加载、插件菜单/权限注册、后端扩展点接入。
- ✅ **前端远程模块接入**：支持插件前端通过 `remoteEntry.js` 动态挂载到主前端宿主。
- ✅ **正式契约发布**：统一发布 `yudream-plugin-spi`、`@yudream/plugin-sdk`、`@yudream/components`，为独立插件仓提供稳定接口。
- ✅ **镜像化部署**：内置 Dockerfile 与 Compose 编排，可直接构建后端/前端镜像并部署。
- ✅ **独立插件仓模板**：提供插件仓模板、边界校验脚本与发布流程样板，方便扩展官方或第三方插件生态。

## 仓库定位

- 核心后端模块：领域层、应用层、基础设施层、接口层、启动模块
- 主前端宿主：后台主应用与插件远程页面容器
- 插件运行时：外部 JAR 加载、插件菜单/权限/前端远程模块接入
- 契约发布：`yudream-plugin-spi`、`@yudream/plugin-sdk`、`@yudream/components`
- 插件模板：独立插件仓模板、CI 模板、边界校验脚本

## 项目生态

| 仓库 | 角色 |
| --- | --- |
| `YuDream-Admin` | 主体平台、插件运行时、契约包发布 |
| `yudream-admin-plugins` | 官方业务插件源码、插件前端 remote 模块、插件 JAR 发布 |

相关入口：

- GitLab Plugins: [yudream/yudream-admin-plugins](https://gitlab.yudream.online/yudream/yudream-admin-plugins)

## 核心特性

- DDD 风格的后端分层结构
- 支持外部插件 JAR 的运行时加载
- 支持前端 remote entry 动态接入
- 提供插件 SPI、SDK、共享组件的正式发布链路
- 支持 Docker 镜像构建与部署
- 提供独立插件仓模板与边界校验脚本

## 技术栈

- Java 21
- Maven 3.9+
- Node.js 22.22+ / 24.15+
- pnpm 11.9+
- Spring Boot 3.5
- Vue 3 + Vite

## 快速开始

### 1. 克隆并安装前端依赖

```powershell
cd yudream-frontend
pnpm install
```

### 2. 构建后端

```powershell
mvn -s ci/maven-settings.xml -pl yudream-bootstrap -am -DskipTests compile
```

### 3. 构建主前端

```powershell
cd yudream-frontend
pnpm --filter @fantastic-admin/core-arco-design-vue run build
```

### 4. 运行拆仓/边界审计

```powershell
& 'C:/Program Files/Git/bin/sh.exe' ci/verify-core-split-readiness.sh
```

### 5. 使用 Docker Compose 部署

```powershell
docker compose pull
docker compose up -d
```

默认部署会启动：

- `backend`: 主后端服务
- `frontend`: 主前端服务
- `watchtower`: 镜像自动更新监控

插件 JAR 默认通过宿主机 `./plugins` 目录挂载到运行容器。

## 仓库结构

```text
yudream-domain/                        核心领域层
yudream-application/                   核心应用层
yudream-infrastructure/                基础设施层与插件运行时
yudream-interfaces/                    接口层
yudream-bootstrap/                     启动模块
yudream-plugins/yudream-plugin-spi/    插件 SPI 契约
yudream-plugins/yudream-sample-plugin/ 示例插件
yudream-frontend/                      主前端宿主与共享前端包
plugins/                               外部插件 JAR 挂载目录
ci/                                    CI 校验与发布脚本
docs/                                  架构、拆仓、发布文档
templates/plugin-repo/                 独立插件仓模板
```

## 插件开发与契约包

当前主体仓对外发布以下契约：

- Maven: `online.yudream.base:yudream-plugin-spi`
- npm: `@yudream/plugin-sdk`
- npm: `@yudream/components`

这些契约用于独立插件仓消费，而不是让业务插件继续和主体仓共享源码工作区。

如果你要新建官方或第三方插件，优先参考：

- [插件仓模板 README](templates/plugin-repo/README.md)
- [插件仓 CI 模板](templates/plugin-repo/.gitlab-ci.yml.example)
- [插件仓 Maven 设置模板](templates/plugin-repo/settings.xml.example)
- [插件仓 npm 设置模板](templates/plugin-repo/.npmrc.example)
- [插件仓 pnpm workspace 模板](templates/plugin-repo/pnpm-workspace.yaml.example)

## CI 与发布

当前仓库 CI 主要负责：

- 核心后端与主前端构建
- 契约包发布与回读校验
- 主体仓/插件仓边界校验
- Docker 镜像打包与推送

默认发布的契约路径：

- `yudream-plugin-spi` -> GitLab Maven Registry
- `@yudream/plugin-sdk` -> npmjs / 可选 GitLab npm Registry
- `@yudream/components` -> npmjs / 可选 GitLab npm Registry

相关文档：

- [GitLab 私有包发布说明](docs/plugin-system/gitlab-private-packages.md)
- [npmjs 公网包发布说明](docs/plugin-system/npmjs-public-packages.md)
- [契约发布校验](docs/plugin-system/contract-validation.md)

## 文档导航

- [拆仓说明](docs/repository-split/README.md)
- [拆仓边界清单](docs/repository-split/staging-boundaries.md)
- [远端发布验收记录](docs/repository-split/remote-release-evidence.md)
- [插件系统规范](docs/plugin-system/specification.md)
- [插件系统教程](docs/plugin-system/tutorial.md)

## 贡献

欢迎提交 Issue 和 Pull Request。

如果改动涉及插件系统、SPI、插件前端接入或仓库边界，建议先阅读相关文档，并优先保证：

- 主体仓不重新耦合官方业务插件源码
- 插件仓只依赖正式发布的契约包
- CI 校验脚本与模板保持同步

## License

[MIT](LICENSE)
