# 官方业务插件默认使用独立仓

这份说明用于明确分仓后的默认规则，避免后续开发又把官方业务插件源码塞回主体仓。

## 默认分工

`core` 仓负责：

- 宿主后端与宿主前端
- 插件运行时
- `yudream-plugin-spi`
- 共享前端包，例如 `@yudream/plugin-sdk`、`@yudream/components`
- 样例插件或迁移期兼容层

独立插件仓负责：

- 官方业务插件后端模块
- 官方业务插件前端 remote 包
- 插件仓自己的构建、打包与发布 CI
- 插件仓自己的边界校验与契约消费校验

## 新增插件时的默认落点

新增官方业务插件时，默认应放在独立插件仓，例如：

```text
yudream-admin-plugins/
  pom.xml
  yudream-plugins/yudream-plugin-demo/
  yudream-frontend/packages/plugin-demo/
```

而不是继续放回主体仓：

```text
yudream-admin-core/
  yudream-plugins/yudream-plugin-demo/
  yudream-frontend/packages/plugin-demo/
```

## 插件仓前端工作区边界

插件仓前端 workspace 应保持为：

```yaml
packages:
  - packages/plugin-*
```

不要恢复 `packages/*`，也不要把 `@yudream/plugin-sdk`、`@yudream/components` 的源码复制进插件仓。

## 允许暂留在 core 的内容

下面两种情况可以暂时留在 `core` 仓：

1. 样例 / 教学插件
2. 迁移期临时兼容层

但这些兼容内容必须同时满足：

- 不能重新加入根 `Maven reactor`
- 不能重新加入 `yudream-frontend/pnpm-workspace.yaml`
- 不能被宿主前端通过本地源码自动发现加载

## 当前稳定契约

独立插件仓与主体仓之间的稳定契约应收敛到：

- Maven: `online.yudream.base:yudream-plugin-spi`
- npm: `@yudream/plugin-sdk`
- npm: `@yudream/components`

当前唯一来源：

- 通用 Maven 依赖和插件优先从阿里云公共仓库消费，未命中时回退 Nexus；YuDream Maven 契约最终从 Nexus `maven-public` 消费
- `@yudream/plugin-sdk` 与 `@yudream/components` 统一从 Nexus `https://nexus.yudream.online/repository/npm-public/` 消费
- GitLab Package Registry 与 npmjs 路径已经废弃；GitLab 只负责源码、CI 和 CI artifacts

## 回归检查

主体仓建议执行：

```sh
sh ci/verify-core-plugin-decoupling.sh
```

独立插件仓建议执行：

```sh
sh ci/verify-plugin-repo-independence.sh
sh ci/verify-core-maven-registry.sh
sh ci/verify-core-npm-contracts.sh
sh ci/verify-plugin-jar-assets.sh
```
