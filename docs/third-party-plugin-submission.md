# 第三方插件市场投稿

第三方作者只能通过 Merge Request（MR）提交投稿材料。MR 中不会运行发布操作，也不会获得或读取 Nexus 写入凭据；作者不能自行将 JAR、市场索引或资源上传到 Nexus。

审核通过后，受信发布者才可以在 **protected tag** 或受保护的手动发布流水线中代发。代发使用独立的受保护、掩码凭据；已发布的 `{code}@{version}` 不可覆盖，修复必须发布新的稳定版本。

## 投稿目录

每次投稿放在受控的 `SUBMISSION_DIR`（通常是 `submission/`）中。校验器只接受该目录内部的文件和相对引用，不会跟随绝对路径、`..` 路径穿越、URL、查询参数或片段。

```text
submission/
  submission.json
  plugin.yml
  store.json
  plugin.jar
  plugin.jar.sha256
  LICENSE
  resources/
    icon.svg
    screenshot.png
```

可从 [`templates/plugin-repo/`](../templates/plugin-repo/) 复制 `plugin.yml.example`、`store.json.example`、`submission.json.example` 和 `LICENSE` 开始。将示例中的版权占位符、作者信息、插件 code、版本和入口类替换成真实值。

## 必填材料

`submission.json` 是投稿清单，声明 code、version、main、作者信息和五个文件的相对路径：`plugin.yml`、`store.json`、`plugin.jar`、SHA-256 文件和许可证。

`store.json` 是市场条目，包含：

- `schemaVersion` 与稳定的 `releaseVersion`；
- `plugin.code`、`plugin.version`、`plugin.main`、展示信息、兼容性和依赖；
- `jar.mavenCoordinates`、相对 JAR 路径和 64 位小写十六进制 SHA-256；
- 许可证相对路径，以及可选图标和截图资源。

`plugin.yml` 是 JAR 根目录运行时描述符，至少包含 `name`、`main`、`version` 和 `description`。`depend` 表示必须先启用的插件，`softdepend` 表示可选插件；市场依赖必须以 `required: true/false` 保持同一语义。

## 一致性与安全约束

投稿校验会拒绝以下情况：

- `submission.json`、`plugin.yml` 与 `store.json` 的 code、version 或 main 不一致；
- 非稳定 SemVer（例如 `1.0`、`v1.0.0`、`1.0.0-SNAPSHOT`、预发布版本）；
- JAR 校验和与 `plugin.jar.sha256` 或 `store.json` 不一致；
- 引用不存在、重复或逃出 `SUBMISSION_DIR` 的许可证、资源、清单或归档文件；
- JAR 中缺少根 `plugin.yml`、与外部 `plugin.yml` 不一致，或包含 `online/yudream/base/plugin/spi/**` 类；
- `depend` / `softdepend` 和市场依赖列表不一致，或 required 语义相反；
- 使用已发布的 `{code}@{version}` 重新投稿。

插件必须依赖正式发布的 SPI/SDK 契约，不能将宿主 SPI 类嵌入自己的 JAR。其他插件的业务 API 也不得被消费者重复打包；详细约束见[插件开发规范](plugin-system/specification.md)。

## 审核与发布边界

审核人员应至少确认：

1. MR 仅包含投稿材料和必要说明，没有改写发布脚本、市场生产索引或 CI 凭据配置；
2. 离线投稿校验通过，资源、许可证和 JAR 都在投稿目录中；
3. code/version/main、依赖语义、兼容范围和 SHA-256 已人工复核；
4. 许可证与作者身份、发布权限和第三方依赖许可可接受；
5. 版本尚未发布。发布成功后版本不可变。

普通分支和 MR 的 `validate:third-party-submission` 任务只执行离线校验，显式不声明 `NEXUS_USERNAME`、`NEXUS_PASSWORD` 或任何写入 token，也不调用 Nexus。未来的代发任务必须同时要求 protected ref、手动触发和发布者权限；Raw 资源写入以 `resource_group` 串行化，避免并发覆盖市场状态。
