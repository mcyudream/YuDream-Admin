# 契约发布：SPI / plugin-sdk / components

> **适用范围**：`yudream-plugin-spi`、`@yudream/plugin-sdk`、`@yudream/components` 三个契约包的发布与下游同步。
> **目标**：版本从源文件读取、按序发布验签、下游只消费已验证版本。

## 1. 铁律

1. 只发布到 `nexus.yudream.online`；GitLab 只承载源码/CI/CI 产物，GitLab Package Registry 与 npmjs 发布已废弃。
2. **版本号永远从源文件读取，不凭记忆**。
3. 顺序不可颠倒：本仓升级并验证 → 提交推送 → tag 流水线发布并等 verify 通过 → 再到 `yudream-admin-plugins` 同步依赖、刷新 lockfile、运行下游校验并**单独提交**。
4. 未验证已发布的版本**不得**用于下游；未明确要求时不做发布后 snapshot 升版。
5. 契约包可分开发布：只发布版本实际变动的包。
6. 发布前若任一仓工作区脏，只改契约发布相关文件，**不得**覆盖无关本地改动。

## 2. 版本源文件

| 包 | 版本源 | 消费侧 |
|---|---|---|
| SPI | `yudream-plugins/yudream-plugin-spi/pom.xml` | 主仓根 `pom.xml` 的 `yudream.plugin.spi.version`（必须与制品版一致）；插件仓只改其根同名属性 |
| plugin-sdk | `yudream-frontend/packages/plugin-sdk/package.json` | 插件仓经 `yudream-frontend/pnpm-workspace.yaml` catalog 消费 |
| components | `yudream-frontend/packages/components/package.json` | 同上 |

- **禁止**在插件仓逐个改插件包 `package.json`：共享前端契约版本统一从 `pnpm-workspace.yaml` catalog 来，改后 `pnpm install` 刷新 `pnpm-lock.yaml`。
- 后端插件从根 Maven 属性继承 SPI 版本，改属性而不是各模块。

## 3. 何时升哪个包

- SPI：Java 插件接口、DTO、生命周期契约、框架服务端口、插件运行时契约变化。
- plugin-sdk：插件前端 API client、TS 契约、宿主-插件前端运行时 API、SDK 导出变化。
- components：共享插件侧 UI 组件或导出变化，或 SDK 发布依赖新组件面。
- 跨前后端契约的变化优先一起发布，插件仓一次同步到位。
- 新框架能力端口（如 `FrameworkServices.graph()`）发布前核验：SPI 源码已声明端口、宿主运行时提供适配、对应版本 SPI 文档写准确签名、插件无法绕过（环境变量/自建客户端）。

## 4. 发布流程

1. 从源文件读当前版本，确认哪些包要动、是 snapshot 维护/首发稳定版/稳定 bump。
2. 只改本次发布的契约包版本；发 SPI 时同步根 `yudream.plugin.spi.version`。
3. 定向验证：
   ```powershell
   mvn -s ci/maven-settings-nexus.xml -f yudream-plugins/yudream-plugin-spi/pom.xml -DskipTests package -B -ntp
   cd yudream-frontend; pnpm install --frozen-lockfile; pnpm --filter @yudream/plugin-sdk run build; pnpm --filter @yudream/components run build
   ```
   可选全量校验：`ci/verify-contract-packages.sh`、`ci/verify-contract-package-tarballs.sh`、`ci/verify-core-contract-publish-pipeline.sh`（CI 任务 `validate:contract-packages` / `validate:contract-package-tarballs` / `validate:contract-publish-pipeline`）。
4. 提交并推送版本改动，然后打 tag 推流水线。
5. 等 publish 与 verify 任务通过，**再**动插件仓。
6. 插件仓：改根 `pom.xml` 属性 + `pnpm-workspace.yaml` catalog，`pnpm install` 刷新 lockfile，跑 `ci/verify-plugin-repo-readiness.sh`，单独提交推送。
7. 汇报必须含：发布的精确版本、tag 名、registry 路径、插件仓是否已同步验证。

## 5. tag 与 CI 任务对照

| tag 形态 | 发布 | 验签 |
|---|---|---|
| `v*` / 裸 `p*` | 全部三个契约包 | 全部 |
| `pspi-*` | 只发 SPI | `publish/verify:maven-plugin-spi` |
| `psdk-*` | 只发 plugin-sdk | npm 发布 + `verify:npm-contracts`（`VERIFY_NPM_PACKAGES` 收窄） |
| `pcomp-*` | 只发 components | 同上 |

tag 触发的任务：`publish:maven-plugin-spi`、`publish:npm-plugin-sdk`、`publish:npm-components`、`verify:maven-plugin-spi`、`verify:npm-contracts`。分包流水线用 `ci/verify-core-remote-release-evidence.sh` 核查时按 tag 名自动跳过另一族任务（或 `EXPECT_MAVEN_SPI_PUBLISH=false` / `EXPECT_NEXUS_NPM_PUBLISH=false` 强制）。

## 6. Registry 与凭据

- 全部契约包只发 `nexus.yudream.online`：Maven 读 `maven-public`，发布到 `maven-releases`/`maven-snapshots`；`@yudream` npm scope 读写 `npm-public`；普通 Maven 依赖先 Aliyun 后 Nexus 兜底。
- 发布与认证验签需要 `NEXUS_USERNAME`/`NEXUS_PASSWORD`（存 `docs/local.md`，不入库）。
- 验签脚本走 Nexus，**禁止**直连 npmjs（会卡死）。

## 7. 常见失败检查

- 缺 Nexus 发布权限：确认 `NEXUS_USERNAME`/`NEXUS_PASSWORD` 与仓库权限。
- 插件仓仍解析旧版本：确认 `pnpm-workspace.yaml` 已改且 `pnpm-lock.yaml` 已重新生成。
- SPI 消费方仍解析旧制品：确认插件仓根 pom 属性已改、目标版本已在 Nexus Maven。
- npm 发布幂等：重打带后缀 tag 可重跑验签；**失败 tag 不可移动**。
- 历史发布细则与 Nexus 包说明见 `docs/plugin-system/gitlab-private-packages.md`、`docs/yd-contract-releases.md`。
