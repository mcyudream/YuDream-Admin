# 插件市场与第三方插件上架

本文说明插件市场的发布模型，以及第三方作者从投稿到上架的完整流程。插件市场将可安装的插件描述、JAR 和资源作为**不可变发布物**管理：已发布的 `{code}@{version}` 不可覆盖，修复必须发布新的稳定版本。

> 主要依据源码与文档：`docs/plugin-store-release.md`、`docs/third-party-plugin-submission.md`、`templates/plugin-repo/`（README、`store.json.example`、`submission.json.example`）、`templates/plugin-repo/docs/plugin-release.md`、`ci/publish-plugin-jars.sh`。

## 1. 总体流程

```mermaid
flowchart TD
    A[作者构建插件 JAR<br/>依赖正式发布的 SPI/SDK] --> B[组装 submission/ 投稿目录<br/>submission.json + plugin.yml + store.json + plugin.jar + SHA-256 + LICENSE + resources]
    B --> C[提交 Merge Request]
    C --> D[MR CI 离线校验 validate:third-party-submission<br/>格式/一致性/JAR 检查，无任何 Nexus 凭据]
    D -->|校验失败| B
    D --> E[审核人人工复核<br/>版权/资源/兼容性/版本唯一性]
    E -->|拒绝| B
    E --> F[受信发布者在 protected tag<br/>或受保护手动流水线代发]
    F --> G[发布 job 写入 Nexus Maven/Raw 仓<br/>凭据仅注入该 job，resource_group 串行化]
    G --> H{code@version 已存在?}
    H -->|是| I[拒绝覆盖]
    H -->|否| J[发布成功，版本永久不可变<br/>保留发布 ref 与流水线作为审计记录]
```

核心原则是**投稿验证与发布职责分离**：MR 只负责提出可复核、可重复校验的材料；审核人与受信发布者负责签发并代发最终不可变版本。普通 MR CI 只能运行本地格式、归档和校验和检查——不得读取或传递 Nexus 写凭据，也不得向 Nexus 上传任何内容。

## 2. 作者侧：准备投稿材料

### 2.1 投稿目录结构

每次投稿放在受控的 `SUBMISSION_DIR`（通常是 `submission/`）中：

```text
submission/
  submission.json      # 投稿清单
  plugin.yml           # 运行时描述符（须与 JAR 根 plugin.yml 一致）
  store.json           # 市场条目
  plugin.jar           # 构建产物
  plugin.jar.sha256    # SHA-256 校验和文件
  LICENSE              # 许可证
  resources/           # 图标、截图等引用资源
    icon.svg
    screenshot.png
```

可从仓库根 `templates/plugin-repo/` 复制 `plugin.yml.example`、`store.json.example`、`submission.json.example` 和 `LICENSE` 开始，替换版权占位符、作者信息、插件 code、版本和入口类。

校验器只接受投稿目录内部的文件和相对引用：绝对路径、`..` 路径穿越、URL、查询参数或片段一律拒绝。

### 2.2 三份描述文件

**`submission.json`**（投稿清单）示例骨架（完整字段见 `templates/plugin-repo/submission.json.example`）：

```json
{
  "schemaVersion": 1,
  "submission": {
    "code": "example-plugin",
    "version": "1.0.0",
    "main": "com.example.yudream.ExamplePlugin",
    "author": { "name": "...", "contact": "..." }
  },
  "files": {
    "pluginYml": "plugin.yml",
    "store": "store.json",
    "jar": "plugin.jar",
    "sha256": "plugin.jar.sha256",
    "license": "LICENSE"
  }
}
```

**`store.json`**（市场条目）示例骨架（完整字段见 `templates/plugin-repo/store.json.example`）：

```json
{
  "schemaVersion": 1,
  "releaseVersion": "1.0.0",
  "plugin": {
    "code": "example-plugin",
    "version": "1.0.0",
    "main": "com.example.yudream.ExamplePlugin",
    "compatibility": {
      "host": ">=1.0.0 <2.0.0",
      "spi": ">=1.0.0 <2.0.0",
      "frontendSdk": ">=1.0.0 <2.0.0"
    },
    "dependencies": []
  },
  "jar": {
    "mavenCoordinates": "com.example.yudream:example-plugin:1.0.0:jar",
    "url": "plugin.jar",
    "sha256": "<64 位小写十六进制 SHA-256>"
  },
  "license": "LICENSE"
}
```

字段要点：

- `submission.json` 声明 code、version、main、作者信息，以及五个文件的相对路径；
- `store.json` 包含展示信息、兼容性范围（`compatibility.host/spi/frontendSdk`）和依赖列表；`jar.sha256` 必须是 64 位小写十六进制；可附带图标与截图相对路径；
- `plugin.yml`（JAR 根运行时描述符）至少包含 `name`、`main`、`version` 和 `description`；`depend` 表示必须先启用的插件，`softdepend` 表示可选插件。

关键一致性要求：**code、version、main 必须在 `submission.json`、外部 `plugin.yml` 与 JAR 内根 `plugin.yml` 三处完全一致**；市场依赖条目的 `required: true/false` 必须与 `depend`/`softdepend` 表达同一语义。

### 2.3 安全与契约约束

- 版本必须是稳定 SemVer（如 `1.0.0`）；`1.0`、`v1.0.0`、`1.0.0-SNAPSHOT` 及预发布版本都会被拒绝。
- 插件必须依赖正式发布的契约模块，JAR 中不得内嵌 `online/yudream/base/plugin/spi/**` 类；其他插件的业务 API 也不得被消费者重复打包（以 `provided` 编译并经 `context.service(...)` 调用）。
- 前端产物必须包含 `META-INF/yudream-plugin/frontend/{pluginCode}/remoteEntry.js`，且不含 SPI 类文件。
- Java Long/Snowflake ID 在插件 DTO、TS 模型、表单与 URL 参数中一律使用 **string**，禁止 `Number(id)`。

## 3. 校验规则清单

投稿校验会拒绝以下情况：

| 类别 | 规则 |
| --- | --- |
| 一致性 | 三份描述文件的 code、version 或 main 不一致 |
| 版本 | 非稳定 SemVer |
| 完整性 | JAR 校验和与 `plugin.jar.sha256` 或 `store.json` 不一致 |
| 引用安全 | 引用不存在、重复或逃出 `SUBMISSION_DIR` 的许可证、资源、清单或归档文件 |
| JAR 内容 | 缺少根 `plugin.yml`、与外部描述符不一致，或包含 SPI 类 |
| 依赖语义 | `depend`/`softdepend` 与市场依赖列表不一致，或 required 语义相反 |
| 唯一性 | 使用已发布的 `{code}@{version}` 重新投稿 |

## 4. 审核人检查清单

审核人员应至少确认：

1. MR 仅包含投稿材料和必要说明，没有改写发布脚本、市场生产索引或 CI 凭据配置；
2. 离线投稿校验通过，资源、许可证和 JAR 都在投稿目录中；
3. code/version/main、依赖语义、兼容范围和 SHA-256 已人工复核；
4. 许可证与作者身份、发布权限和第三方依赖许可可接受；
5. 该 `{code}@{version}` 尚未发布。

审核通过并不向 MR 开放任何 Nexus 凭据。

## 5. 发布者侧：代发与官方插件发布

### 5.1 第三方代发

审核通过后，平台发布者可在受保护 tag 或受保护的手动流水线中代发。写入 Maven 或 Raw 仓的 job 必须同时满足：

- ref 为 protected tag，或受保护分支上的手动发布任务；
- 写凭据仅作为 protected、masked CI variables 注入发布 job（不进入普通 MR 任务）；
- Raw 市场资源写入配置 `resource_group`，串行化同一市场的更新，避免并发覆盖市场状态；
- 发布前确认 `{code}@{version}` 尚未发布，发布后拒绝覆盖。

代发完成后应保留发布 ref、流水线和最终产物坐标作为审计记录。

### 5.2 官方插件发布契约

官方业务插件在独立插件仓（由 `templates/plugin-repo/` 初始化）中经 `publish-plugin` → `verify-publish` 两段 CI 发布：

1. 在官方 tag 中仅选择 `release/plugins.txt` 明确列出的完整 `yudream-plugin-*` artifactId（当前为 ai-chatbot、alipay、authlib-injector、minecraft-activity-proof、minecraft-server、project-progress、qq-binding、qqbot-automation、student-info、wallet、web-card、world-map、yudream-launcher、yudream-skin 共 14 个）；
2. 使用 Maven `-pl <selected modules> -am` 打包，仅将这些模块的最终 JAR 放入 staging，避免把 `-am` 前置依赖误发布；每个模块只选一个最终包（优先 `*-shaded.jar`），打包后校验 JAR 内含 `remoteEntry.js` 且不含 SPI 类；
3. 为同一选择结果生成并发布 `plugins.manifest.tsv` 与 `sha256sum.txt` catalog；
4. 从 Nexus `maven-public` 回读 catalog 与所选 JAR，逐项核对校验和与前端资源。

选择机制要点：

- `PLUGIN_RELEASE_ONLY=1` 启用清单选择模式（tag 流水线已固定设置）；
- `PLUGIN_RELEASE_MODULES` 可临时覆盖，值为逗号或空白分隔的严格白名单——空项、重复或未知模块均使流水线失败；
- 此机制**不使用 changed-plugin detection**，发布范围永远来自显式清单或已校验的覆盖值；
- 若提供 `CI_COMMIT_TAG`，还会校验每个已选 JAR 的根 `plugin.yml` 版本是否等于去掉 `v` 前缀后的发布版本（`ci/verify-plugin-release-selection.sh`）。

默认制品地址：

```text
https://nexus.yudream.online/repository/maven-releases/online/yudream/plugins/
```

每个插件使用 `online.yudream.plugins:<artifactId>:<tag version>:jar` 坐标；catalog 使用 `online.yudream.plugins:plugin-catalog:<tag version>:tsv`。官方发布凭据同样只能配置为受保护、掩码变量，发布只能由受保护 `v*` tag 或手动 job 执行。

## 6. 相关文档

- [plugin/specification.md](./specification.md)：插件结构与运行时规范
- [plugin/dev-tools.md](./dev-tools.md)：开发模式与调试浮窗
- [plugin/repository.md](./repository.md)：插件仓布局
