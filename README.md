# YuDream Admin Core

## Readiness Audit

```powershell
& 'C:/Program Files/Git/bin/sh.exe' ci/verify-core-split-readiness.sh
```

## Staging Guide

- [docs/repository-split/staging-boundaries.md](docs/repository-split/staging-boundaries.md)
- `ci/stage-core-contract-split.sh`
- `ci/stage-core-host-cleanup.sh`

## Remote Evidence

```powershell
& 'C:/Program Files/Git/bin/sh.exe' ci/verify-core-remote-release-evidence.sh
```

- [docs/repository-split/remote-release-evidence.md](docs/repository-split/remote-release-evidence.md)

这是 YuDream Admin 的主体仓，当前职责聚焦在：

- 核心后端模块
- 核心前端宿主模块
- 插件运行时
- 插件契约发布

官方业务插件已经按默认方向迁移到独立插件仓，不再和主体仓共用同一条业务插件构建链路。

## 当前目录结构

```text
yudream-domain/                     核心领域层
yudream-application/                核心应用层
yudream-infrastructure/             核心基础设施层与插件运行时
yudream-interfaces/                 核心接口层
yudream-bootstrap/                  核心启动模块
yudream-plugins/yudream-plugin-spi/ 插件 SPI 契约模块
yudream-plugins/yudream-sample-plugin/ 样例插件
yudream-frontend/                   核心前端宿主与共享插件 SDK
plugins/                            运行时外部插件 JAR 目录
ci/                                 核心 CI 校验脚本
docs/repository-split/              分仓说明
templates/plugin-repo/              独立插件仓模板
```

## 当前边界

- 已迁移的官方业务插件源码不再保留在主体仓活跃路径中
- 主体仓前端不会再从本地 `packages/plugin-*` 自动发现业务插件源码
- 核心 GitLab CI 不再负责构建业务插件前端和业务插件 JAR
- 业务插件默认以外部 JAR 形式放入 `plugins/` 目录，由宿主运行时加载
- 主体仓 CI 现在会在分支和 tag 上直接验证拆分边界；发布任务仍然只在 tag 流水线执行

## 核心构建

后端：

```powershell
mvn -pl yudream-bootstrap -am -DskipTests compile
```

前端：

```powershell
cd yudream-frontend
pnpm install
pnpm --filter @fantastic-admin/core-arco-design-vue build
```

## 对外发布的契约包

主体仓当前负责发布给独立插件仓消费的契约包：

- Maven: `online.yudream.base:yudream-plugin-spi`
- npm: `@yudream/plugin-sdk`
- npm: `@yudream/components`

默认策略：

- Maven 契约继续发布到核心仓 GitLab Maven Registry
- npm 契约默认面向 npmjs
- GitLab npm 私包发布改为显式开关 `GITLAB_NPM_PUBLISH_ENABLED=true` 时才执行

相关说明：

- [GitLab 私有包发布说明](docs/plugin-system/gitlab-private-packages.md)
- [npmjs 公网包发布说明](docs/plugin-system/npmjs-public-packages.md)
- [契约发布校验](docs/plugin-system/contract-validation.md)

## 插件运行时

宿主运行时默认只扫描：

```text
plugins/
```

独立插件仓构建出的 JAR 应复制或挂载到该目录，再由核心系统加载。

相关说明：

- [外部插件目录说明](plugins/README.md)

## 独立插件仓入口

如果你要继续开发官方业务插件，优先使用独立插件仓路线：

- [分仓说明](docs/repository-split/README.md)
- [插件仓模板说明](templates/plugin-repo/README.md)
- [插件仓 CI 模板](templates/plugin-repo/.gitlab-ci.yml.example)
- [插件仓 Maven 设置模板](templates/plugin-repo/settings.xml.example)
- [插件仓 npm 设置模板](templates/plugin-repo/.npmrc.example)
- [插件仓 pnpm workspace 模板](templates/plugin-repo/pnpm-workspace.yaml.example)
