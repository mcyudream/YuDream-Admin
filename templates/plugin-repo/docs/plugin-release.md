# 插件仓发布说明

## 目标

独立插件仓除了独立构建，还应独立发布可部署插件 JAR。

## CI 流程

模板中的 `.gitlab-ci.yml.example` 默认拆成五段：

1. `validate`
   - `ci/verify-plugin-repo-independence.sh`
   - `ci/verify-core-maven-registry.sh`
   - `ci/verify-plugin-maven-boundary.sh`
   - `ci/verify-core-npm-contracts.sh`
   - `ci/verify-plugin-publish-pipeline.sh`
   - `ci/verify-plugin-release-selection.sh`
2. `build-frontend`
3. `package-plugin`
   - 使用单独的干净 Maven 本地仓目录重新解析依赖
   - 普通分支运行完整 Maven reactor；tag 只执行显式选择模块的 `mvn ... -pl <modules> -am`
   - 完成 Maven 打包后校验最终插件 JAR 内的 `remoteEntry.js`
4. `publish-plugin`
5. `verify-publish`

`publish-plugin` 只在 `v*` Git tag 流水线执行，并调用 `ci/publish-plugin-jars.sh` 上传插件包。`verify-publish` 随后调用 `ci/verify-published-plugin-jars.sh`，从 Nexus `maven-public` 回读 catalog 和本次选择的最终 JAR，逐项核对索引和校验和。

## 显式发布选择

官方 tag 发布使用仓库内的 [`release/plugins.txt`](../release/plugins.txt) 作为唯一的默认选择来源。当前清单为：

```text
yudream-plugin-ai-chatbot
yudream-plugin-alipay
yudream-plugin-authlib-injector
yudream-plugin-minecraft-activity-proof
yudream-plugin-minecraft-server
yudream-plugin-project-progress
yudream-plugin-qq-binding
yudream-plugin-qqbot-automation
yudream-plugin-student-info
yudream-plugin-wallet
yudream-plugin-web-card
yudream-plugin-world-map
yudream-plugin-yudream-launcher
yudream-plugin-yudream-skin
```

每行必须是唯一的实际插件模块 ID。tag 打包、JAR staging、Maven 发布、已发布制品回读校验以及 `plugins.manifest.tsv`/`sha256sum.txt` catalog 都使用同一份选择结果；`-am` 构建的依赖模块不会被错误放入 `dist/plugins` 或 catalog。

可用环境变量：

- `PLUGIN_RELEASE_ONLY=1`：启用 `release/plugins.txt` 的选择模式；tag pipeline 已固定设置。
- `PLUGIN_RELEASE_MODULES`：以逗号或空白分隔的临时覆盖列表。每个 ID 必须出现在 `release/plugins.txt` 中，且不允许空项、重复或未知 ID。

普通分支和本地未设置上述变量时，保留原有行为：处理全部已发现模块。这里**没有 changed-plugin detection**；是否发布完全由显式清单或严格校验过的覆盖值决定。

`ci/verify-plugin-release-selection.sh` 会验证清单和错误覆盖；若提供 `CI_COMMIT_TAG` 或 `PLUGIN_PACKAGE_VERSION`，还会校验每个已选择 JAR 的根 `plugin.yml` 版本是否等于去掉 `v` 前缀后的发布版本。

## JAR 与前端约束

发布脚本为每个选择的插件模块只选择一个最终包：优先 `*-shaded.jar`，否则普通 `*.jar`。`ci/verify-plugin-jar-assets.sh` 还应保证最终插件 JAR 包含 `META-INF/yudream-plugin/frontend/{pluginCode}/remoteEntry.js`，且不包含 `online/yudream/base/plugin/spi/*` 类文件。

前端产物应将 `remoteEntry.js`、其独立 JS chunk、CSS、图片和字体一并放入 `META-INF/yudream-plugin/frontend/{pluginCode}/`。需要由宿主预加载的 CSS 或 module script 在 `PluginFrontendModule.styles` / `scripts` 声明相对路径；动态 import chunk 不必重复声明。仍可使用 `styles.css?inline` 在 `install()` 注入样式，作为无需独立 CSS 的兼容方式。

## 前端工作区边界

插件仓前端工作区应保持为：

```yaml
packages:
  - packages/plugin-*
```

不要恢复 `packages/*`，也不要把 `@yudream/plugin-sdk`、`@yudream/components` 的源码放进插件仓。插件仓 CI 的前端构建入口也应只匹配 `yudream-frontend/packages/plugin-*/package.json`。

## 发布产物与变量

额外发布 Maven catalog 制品：

- `sha256sum.txt`
- `plugins.manifest.tsv`

默认地址：

```text
https://nexus.yudream.online/repository/maven-releases/online/yudream/plugins/
```

每个插件使用 `online.yudream.plugins:<artifactId>:<tag version>:jar` 坐标；catalog 使用 `online.yudream.plugins:plugin-catalog:<tag version>:tsv`，校验和使用同一制品的 `sha256` classifier。

默认依赖 `CI_COMMIT_TAG`、`NEXUS_USERNAME`、`NEXUS_PASSWORD`、`NEXUS_MAVEN_RELEASES_URL` 和 `NEXUS_MAVEN_PUBLIC_URL`。可选 `PLUGIN_PACKAGE_VERSION` 覆盖版本；其余选择变量见上文。
