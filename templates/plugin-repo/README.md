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

第三方作者不使用本模板的官方发布 job，也不配置 `NEXUS_USERNAME`、`NEXUS_PASSWORD` 或其他写凭据。作者只能通过 MR 提交可审计的投稿材料；普通 MR CI 只作本地校验，绝不上传 Nexus。

1. 在受控 `submission/` 目录中复制 `plugin.yml.example`、`store.json.example`、`submission.json.example` 和 `LICENSE`。
2. 添加构建完成的 `plugin.jar`、其 SHA-256 文件，以及 `store.json` 引用的图标、截图等资源。
3. 使用稳定 SemVer（如 `1.0.0`），并让 `submission.json`、`plugin.yml`、`store.json` 与 JAR 内根 `plugin.yml` 的 code、version、main 完全一致。
4. `depend` 是必需依赖，`softdepend` 是可选依赖；市场条目的 `dependencies[].required` 必须表达相同语义。
5. 确保 JAR 不包含 `online/yudream/base/plugin/spi/**`。SPI、SDK 和组件仅作为已发布依赖使用。
6. 提交 MR，等待许可证、版本、资源、依赖、校验和和归档安全审核。发布版本不可覆盖；修复必须增加版本号。

审核通过后，只有受信发布者才能在 protected tag 或受保护手动发布流水线中代发。写入凭据仅注入该发布 job，Raw 市场资源写入应使用串行 `resource_group`。完整格式和审核清单见 [`docs/third-party-plugin-submission.md`](../../docs/third-party-plugin-submission.md)。
