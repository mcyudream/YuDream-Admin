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

## 6. 插件市场源：多源订阅与公开社区

「插件市场源」是一个可选的平台能力（code `plugin-market-source`，能力中心「插件分发」分组）。开启后后台市场合并各启用源目录，公开站提供 `/market` 社区页；关闭时**不注册本机源、不回落 Nexus/`store-root-url`**，市场列表为空，插件管理/上传/回滚不受影响。存量部署升级后，未开启能力时市场为空是有意语义。

### 6.1 双闸门

- 项目闸门：`yudream.platform.capabilities.plugin-market-source.enabled`（环境变量 `PLATFORM_PLUGIN_MARKET_SOURCE_ENABLED`，默认开）。关闭时市场源管理端点不注册、内置源不播种、公开 v2/legacy 端点不存在。
- 应用闸门：能力未在「平台能力」中启用时，源管理与安装/更新一律拒绝；列表返回空；公开 `/market` 跳转登录。
- 公开社区开关：能力配置键 `publicEnabled`（默认开）。关闭后公开 `/market` 与 v2/legacy 协议停止服务，后台市场订阅与发布仍可用。

### 6.2 源管理

管理员在「平台 → 插件中心 → 市场源管理」维护源列表（权限码 `platform:plugin-market-source:view/create/edit/delete/run`）。发布与审核已拆到同分组下的「插件发布」（作者工作台，`upload`）和「发布审核」（`accept`），三个菜单均映射能力 `plugin-market-source`。公开 `/market` 只发现与下载，不上传：

- 内置源 `default` 类型为 `LOCAL`（本机插件市场）：进程内直读本机 `PUBLISHED` 发布物，无需地址与同步，不可删除；
- 新增源类型只能是 `V2_API`（正式协议，rootUrl 为 `https://host/api/public/plugin-market`）或 `STATIC_INDEX`（legacy `index.json` 完整地址）；禁止创建 `LOCAL`；
- 私有源可配置 Bearer 令牌，令牌经主密钥（`YUDREAM_CREDENTIAL_KEY`）加密存储，接口只返回「是否已配置」；
- 远程源可手动「同步」或「全部同步」：`V2_API` 分页拉 `/api/v2/plugins` 与详情构造结构化快照，`STATIC_INDEX` 仍走根索引 → 插件 index → 最新 descriptor；`LOCAL` 无需同步；单源失败只标记该源异常；
- 后台市场列表读快照（LOCAL 为内存目录），不产生实时外呼；安装/更新按来源下载并校验 SHA-256。

### 6.3 多源合并与安装来源

- 市场列表对各启用源的插件合并去重：同 `code` 取最高 SemVer 版本，版本相同时优先展示已安装插件的来源源，否则按源排序；
- 详情页版本列表合并各源并标注来源；安装/更新请求可显式指定 `sourceCode`，缺省按「安装来源 → 源优先级」解析；
- 安装/更新成功后来源 `marketSourceCode` 记录在本地插件上，后续更新检查默认跟随该来源，并在其他源存在更高版本时自动跨源升级；
- 市场页在启用源多于一个时提供「来源」筛选，卡片与版本行标注来源名称。

### 6.4 源契约

正式源协议是 **Market Source Protocol v2**（裸 JSON REST，搜索/分页/版本/下载，含分类与标签）。静态 `schemaVersion=1` `index.json` 仅作 legacy 兼容，可作为 `STATIC_INDEX` 源手动添加（含原 Nexus 目录）。YuDream 实例内置供给端：发布、审核、编辑、删除、公开社区与 v2 目录，见[自托管市场源](./market-source)。

## 7. 相关文档

- [plugin/specification.md](./specification.md)：插件结构与运行时规范
- [plugin/dev-tools.md](./dev-tools.md)：开发模式与调试浮窗
- [plugin/repository.md](./repository.md)：插件仓布局
