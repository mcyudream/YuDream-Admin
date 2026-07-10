# 仓库拆分说明

## 当前结论

主体仓和插件仓的主链路已经拆开：

- 主体仓负责核心后端、核心前端宿主、插件运行时、SPI/SDK/共享组件发布
- 插件仓负责官方业务插件源码、插件前端 remote 包、插件 JAR 打包与发布

## 当前成立的事实

1. 主体仓根 `pom.xml` 只保留核心模块与 `yudream-plugin-spi`
2. 主体仓前端不再从本地 `packages/plugin-*` 自动发现官方业务插件源码
3. 主体仓 pnpm workspace 不再纳入业务插件前端包
4. 主体仓 CI 会在分支和 tag 上直接校验拆分边界
5. 主体仓发布件中：
   - `yudream-plugin-spi` 的 release 发布到 Nexus `maven-releases`，snapshot 发布到 `maven-snapshots`
   - 通用 Maven 依赖和插件优先从阿里云公共仓库拉取，未命中时回退 Nexus；YuDream Maven 契约最终从 Nexus `maven-public` 拉取
   - `@yudream/plugin-sdk`、`@yudream/components` 统一通过 Nexus `npm-public` 发布和拉取
6. 插件仓只消费正式发布的契约包，不再依赖主体仓 `root parent` 或本地共享源码
7. 插件仓前端 workspace 只允许 `packages/plugin-*`
8. 插件仓会自动校验最终 JAR 内存在 `META-INF/yudream-plugin/frontend/*/remoteEntry.js`
9. 插件仓 tag 流水线把插件 JAR 发布到 Nexus `maven-releases`

GitLab 只负责源码托管、CI 流水线和 CI artifacts。GitLab Package Registry 与 npmjs 发布/拉取路径均已废弃。

## 推荐仓库布局

最小可落地版本：

```text
yudream-admin-core
yudream-admin-plugins
```

其中：

- `yudream-admin-core` 对应当前主体仓
- `yudream-admin-plugins` 对应当前官方业务插件独立仓

## 主体仓保留内容

- `yudream-plugins/yudream-plugin-spi`
- `yudream-plugins/yudream-sample-plugin`
- 宿主运行时与宿主前端
- 共享前端包，例如：
  - `yudream-frontend/packages/plugin-sdk`
  - `yudream-frontend/packages/components`
  - `yudream-frontend/packages/dataviz`
  - `yudream-frontend/packages/settings`
  - `yudream-frontend/packages/types`

## 插件仓承载内容

- 官方业务插件后端模块
- 官方业务插件前端 remote 包
- 插件仓自己的独立 GitLab CI
- 插件 JAR 构建与发布链

## 回归校验

主体仓：

```sh
sh ci/verify-core-plugin-decoupling.sh
sh ci/verify-contract-packages.sh
sh ci/verify-contract-package-tarballs.sh
sh ci/verify-plugin-repo-template.sh
```

插件仓：

```sh
sh ci/verify-plugin-repo-independence.sh
sh ci/verify-core-maven-registry.sh
sh ci/verify-core-npm-contracts.sh
sh ci/verify-plugin-jar-assets.sh
```
