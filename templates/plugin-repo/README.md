# 插件仓模板

这个目录用于初始化未来的独立插件仓，例如：

- `yudream-admin-plugins`

## 模板包含

- `.gitlab-ci.yml.example`
- `.npmrc.example`
- `settings.xml.example`
- `pnpm-workspace.yaml.example`
- `ci/verify-plugin-repo-independence.sh`
- `ci/stage-plugin-repo-foundation.sh`
- `ci/stage-plugin-source-migration.sh`
- `ci/verify-core-maven-registry.sh`
- `ci/verify-plugin-maven-boundary.sh`
- `ci/verify-core-npm-contracts.sh`
- `ci/verify-plugin-jar-assets.sh`
- `ci/verify-plugin-release-selection.sh`
- `ci/publish-plugin-jars.sh`
- `ci/verify-published-plugin-jars.sh`
- `release/plugins.txt`（官方 tag 发布的显式模块清单）
- `docs/plugin-release.md`
- `plugin.yml.example`
- `store.json.example`
- `submission.json.example`
- `LICENSE`（第三方投稿许可证模板）

## 默认定位

这个模板面向“官方业务插件独立仓”场景。

建议放在独立插件仓中的内容：

- 官方业务插件后端模块
- 官方业务插件前端 remote 包
- 插件仓自己的独立 GitLab CI

`core` 仓更适合保留：

- `yudream-plugin-spi`
- 宿主运行时
- 共享前端包，例如 `@yudream/plugin-sdk`、`@yudream/components`
- 样例插件
- 迁移期间必要的兼容层

换句话说，新建官方业务插件时，优先在这个模板初始化出的独立仓中开发，而不是继续把源码回填到主体仓。

## 使用建议

1. 复制模板到新仓根目录
2. 在新仓 GitLab CI variables 中配置受保护、掩码的发布凭据，并保护 `v*` tag：
   - `NEXUS_USERNAME`
   - `NEXUS_PASSWORD`
   - 可选自托管市场：`YUDREAM_MARKET_URL`、`YUDREAM_MARKET_API_KEY`（API Key 勾选 `platform:plugin-market-source:upload`）
3. Maven 优先从阿里云公共仓库拉取通用依赖，缺失时回退 Nexus；YuDream 契约从 `maven-public` 拉取，插件 JAR 发布到 `maven-releases`
4. 让插件仓只依赖正式发布的：
   - Maven: `online.yudream.base:yudream-plugin-spi`
   - npm: `@yudream/plugin-sdk`
   - npm: `@yudream/components`
5. 第三方 npm 包继续使用公共源，`@yudream` scope 统一从 Nexus `npm-public` 拉取
6. 前端 workspace 只保留 `packages/plugin-*`，不要恢复成 `packages/*`
7. 插件仓 CI 的前端构建入口也只匹配 `yudream-frontend/packages/plugin-*/package.json`
8. 共享包只使用公开入口，例如 `@yudream/plugin-sdk`、`@yudream/plugin-sdk/vite-shared`，不要依赖 `src/*` 内部路径
9. 官方 tag 发布由 `release/plugins.txt` 明确选择模块；默认清单包含 14 个完整 `yudream-plugin-*` artifactId。不使用 changed-plugin detection。
10. 需要临时缩小 tag 发布范围时，设置严格校验的 `PLUGIN_RELEASE_MODULES`（逗号或空白分隔）；`PLUGIN_RELEASE_ONLY=1` 使用完整显式清单。未知、空白或重复 ID 会使流水线失败。

完整的选择、版本和 catalog 规则见 [`docs/plugin-release.md`](docs/plugin-release.md)。

## 第三方市场投稿

第三方作者**不要**使用本模板的 Nexus 发布 job，也不要配置 `NEXUS_USERNAME` / `NEXUS_PASSWORD`。上架走自托管市场源，两条路：

1. **开放站点**：向目标站点申请 `platform:plugin-market-source:upload`，在后台「插件发布」上传 JAR；或配置受保护的 `YUDREAM_MARKET_URL` + `YUDREAM_MARKET_API_KEY`，用 `publish:market` 打受保护 `v*` tag。
2. **自托管**：自己启用能力 `plugin-market-source`，把本机 LOCAL 当供给方，对外 v2 基址给订阅方添加为 `V2_API` 源。

JAR 根须有 `plugin.yml`，依赖正式发布的 SPI/SDK，不得内嵌 `online/yudream/base/plugin/spi/**`。`{code}@{pluginVersion}` 不可覆盖。不要组主仓 `submission/` MR。完整步骤见主仓 [`docs/third-party-plugin-submission.md`](../../docs/third-party-plugin-submission.md) 与文档站 [插件市场与第三方上架](https://ydadocs.yudream.online/plugin/marketplace)。
