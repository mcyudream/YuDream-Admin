# 插件市场发布

插件市场将可安装的插件描述、JAR 和资源作为不可变发布物管理。官方插件继续复用现有 `ci/publish-plugin-jars.sh` 的 Maven catalog 产物（`plugins.manifest.tsv` 和 `sha256sum.txt`）；该兼容契约不得因第三方投稿流程而改变。

## 第三方投稿与代发

第三方作者不直接发布。作者只能通过 MR 提交受控 `SUBMISSION_DIR` 中的投稿材料，格式见[第三方插件市场投稿](third-party-plugin-submission.md)。普通 MR CI 只能运行本地格式、归档和校验和检查：不得读取、声明或传递 Nexus 写凭据，也不得向 Nexus 上传 JAR、索引或资源。

审核通过后，平台发布者可在受保护 tag 或受保护的手动流水线中代发。写入 Maven 或 Raw 仓的 job 必须同时满足：

- ref 为 protected tag，或受保护分支上的手动发布任务；
- 写凭据仅作为 protected、masked CI variables 注入发布 job；
- Raw 市场资源写入配置 `resource_group`，以串行化同一市场的更新；
- 先确认 `{plugin code}@{version}` 尚未发布，发布后拒绝覆盖。

这意味着投稿验证和发布职责分离：MR 负责提出可复核的、可重复校验的材料，审核与受信发布者负责签发并代发最终不可变版本。

## 作者与审核人检查

作者需提交 `submission.json`、`plugin.yml`、`store.json`、`plugin.jar`、SHA-256、许可证和所引用资源；code/version/main 必须在三份描述中一致，版本为稳定 SemVer，依赖的 required/optional 语义一致，JAR 不得内嵌 SPI。

审核人应运行投稿校验，并核对版权、资源、兼容性和版本唯一性。审核通过并不向 MR 开放任何 Nexus 凭据。发布者代发后，应保留发布 ref、流水线和最终产物坐标作为审计记录。

## 现有官方发布契约

独立官方插件仓的 `publish-plugin` 与 `verify-publish` 链路仍然：

1. 在官方 tag 中仅选择 `release/plugins.txt` 明确列出的 14 个完整 `yudream-plugin-*` artifactId；
2. 使用 Maven `-pl <selected modules> -am` 打包，并仅将这些模块的最终 JAR 放入 staging，避免将 `-am` 前置依赖误发布；
3. 为相同选择结果生成并发布 `plugins.manifest.tsv` 与 `sha256sum.txt`；
4. 从 Nexus `maven-public` 回读 catalog 和选择的 JAR，核对校验和与前端资源。

`PLUGIN_RELEASE_ONLY=1` 启用该清单，`PLUGIN_RELEASE_MODULES` 可用逗号或空白分隔的严格白名单覆盖它；空项、重复或未知模块均失败。普通分支/本地未启用选择时仍保留全模块行为。此机制**不使用 changed-plugin detection**：发布范围永远来自明确清单或已校验的覆盖值。

官方发布凭据同样只能配置为受保护、掩码变量，并且发布只能由受保护 `v*` tag/manual job 执行。该文档不引入新的 Nexus endpoint、账号或上传命令。
