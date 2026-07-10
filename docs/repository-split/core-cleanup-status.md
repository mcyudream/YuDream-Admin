# Core 清理状态

这份文档记录主体仓在“官方业务插件迁移到独立插件仓”之后的当前清理状态。

## 已完成

- 根 `Maven reactor` 只保留 `core` 模块与 `yudream-plugin-spi`
- 主体前端 pnpm workspace 只保留共享包，不再包含官方业务插件前端包
- 宿主前端运行时不再从本地 `packages/plugin-*` 自动发现官方业务插件源码
- 主体仓与插件仓都已接入边界校验脚本
- 主体仓活跃路径中已移除迁移后的官方业务插件前后端源码目录
- `yudream-plugin-spi` 统一发布到 Nexus `maven-releases` / `maven-snapshots`，并通过 `maven-public` 回读
- `@yudream/plugin-sdk` 与 `@yudream/components` 统一通过 Nexus `npm-public` 发布和拉取
- GitLab Package Registry、npmjs 和 CI Job Token 包读取链路已废弃；GitLab 只保留源码、CI 和 CI artifacts
- 主体仓模板自检已接入 CI，防止插件仓模板漂回旧形态

## 已从主体仓活跃路径移除的官方业务插件

- `yudream-plugin-alipay`
- `yudream-plugin-authlib-injector`
- `yudream-plugin-minecraft-activity-proof`
- `yudream-plugin-minecraft-server`
- `yudream-plugin-project-progress`
- `yudream-plugin-student-info`
- `yudream-plugin-wallet`
- `yudream-plugin-yudream-skin`

对应前端包也已从下面的活跃路径移除：

```text
yudream-frontend/packages/plugin-*
```

## 仍应保留在 core 的内容

- `yudream-plugins/yudream-plugin-spi`
- `yudream-plugins/yudream-sample-plugin`
- 宿主运行时与宿主前端
- 共享前端包，例如：
  - `yudream-frontend/packages/plugin-sdk`
  - `yudream-frontend/packages/components`
  - `yudream-frontend/packages/dataviz`

## 当前回归检查

```sh
sh ci/verify-core-plugin-decoupling.sh
sh ci/verify-contract-packages.sh
sh ci/verify-contract-package-tarballs.sh
sh ci/verify-plugin-repo-template.sh
```

通过标准：

- 已迁移插件目录不再出现在主体仓活跃路径
- 主体 `reactor`、前端 `workspace`、宿主前端运行时都不会重新把这些插件纳入默认链路
- 独立插件仓模板继续反映当前的拆分边界与发布链
