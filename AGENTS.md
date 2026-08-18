# YuDream Admin — 工程规则

本文件适用于 `D:/code/yudream-admim` 下的全部工作。它是项目规范的索引；修改后端代码前必须完整阅读 `.codex/skills/yudream-ddd-architecture/SKILL.md`，契约发布前必须完整阅读 `.codex/skills/yudream-contract-release/SKILL.md` 及其 `references/`。本文件与 skill 内容冲突时，以 skill 的最新内容为准，并在同一工作项内同步更新两边。

- 后端 DDD 分层、平台能力、插件架构、Excel、中文文本、Git 工作流：`.codex/skills/yudream-ddd-architecture/SKILL.md` 与 `references/knowledge.json`
- SPI/SDK/components 契约发布与下游同步：`.codex/skills/yudream-contract-release/SKILL.md`、`references/file-map.md`、`references/release-workflow.md`
- 前端框架与 monorepo 规范：`yudream-frontend/AGENTS.md`

## 1. 工作范围与模块布局

- `yudream-domain`：聚合、值对象、枚举、仓储接口、领域服务；禁止框架/Web 依赖。
- `yudream-application`：cmd/query/dto、应用 assembler、应用 service（编排仓储、领域服务、事务、校验）。
- `yudream-infrastructure`：dataobj、mapper、仓储实现、外部技术网关。
- `yudream-interfaces`：controller、request/res、接口 assembler、Excel row。
- `yudream-bootstrap`：启动与装配。
- `yudream-plugins/yudream-plugin-spi`：第三方插件唯一允许依赖的编译期契约模块。
- `yudream-frontend`：宿主前端与共享包；官方业务插件的前后端源码一律在独立的 `yudream-admin-plugins` 仓，不再回流本仓。

修改插件架构前先确定受影响边界，先参考行为最接近的既有模块；分层改造以 `system/user` 包为基线。保留用户已有的未提交修改，禁止覆盖或顺手重构无关文件。

## 2. DDD 分层硬规则

- 应用 service 不得接收接口 `request`、不得返回接口 `res`；非琐碎的 `domain <-> DTO` 转换放应用 assembler。
- dataobj 不外泄到应用/接口层；`domain <-> dataobj` 只在 infra mapper。
- Controller 硬规则：禁止 `new XxxCmd/Res/ExcelRow`、响应 `builder()`、私有 `toXxx/parseXxx`、Excel 模板构造和业务不变量；只做边界校验、调用应用 service、经 assembler 返回 `Result`。
- 分层归属：接口 assembler 负责 `request -> cmd`、`DTO -> res`、Excel 行映射；应用 assembler 负责领域到应用 DTO；infra mapper 负责领域到 dataobj。
- 领域异常与错误文案使用正常 UTF-8 中文；禁止 `\uXXXX` 转义和乱码。PowerShell 打印乱码不构成修改文件的理由，只修复源文件中真实存在的乱码。

## 3. 平台能力（platform vs system）

- 安全与身份等基线能力（接口加密、双 token、API Key、Passkey、OAuth）属于 `system`；SSE、WebSocket、MQ、Neo4j、Python Runtime、HTTP 集成、文档生成、CMS、AI/Agent 等可动态加载能力属于 `platform`。
- 平台能力运行必须过两道闸门：项目闸门（配置/`@ConditionalOnProperty` 决定是否允许加载 provider）与应用闸门（应用层每次用例前 `ensureEnabled(...)` 检查持久化状态）。项目闸门不允许的能力不得注册端点、不得启动恢复。
- Infra provider 只是工具包装：构造与 `enable(config)` 不得建立外部连接、声明队列或启动长驻资源；连接只在两道闸门通过且真实业务/连接/健康检查动作需要时创建，disable 时关闭清理。
- 能力间运行时依赖必须声明在 `CapabilityDescriptor.dependencies`；依赖不可用时拒绝启用，禁用依赖必须级联禁用依赖方。

## 4. 插件架构要点

- `yudream-plugin-spi` 是插件唯一可依赖的宿主模块；插件代码禁止依赖 domain/application/infrastructure/interfaces/bootstrap。
- JAR 根必须有权威 `plugin.yml`（`name`/`main`/`version`）；`name` 是唯一稳定 code，`displayName` 仅展示；`depend` 为硬依赖、`softdepend` 为可选依赖。禁止打包 `META-INF/services/...YuDreamPlugin`。
- 插件按职责分包（domain/application/infrastructure/interfaces/migration/frontend/bootstrap）；入口类只做装配与生命周期，所有注册走 `PluginContext.registerXxx(...)`，保证 disable/unload 可完整回收。
- 插件调用宿主能力只能走 SPI 端口；需要新能力时先发布稳定 SPI 端口/DTO，再在宿主实现适配。插件间业务 API 放 provider JAR 的稳定最小 `*.api` 包，consumer 以 `provided` 编译并通过 `context.service(...)` 调用；禁止复制 provider API。
- 硬依赖必须先于 consumer 加载启用；软依赖缺失不阻塞 consumer，相关菜单/路由/端点必须条件注册并显式降级。provider 存在已加载的硬/软依赖方时禁止卸载。
- 插件 HTTP 端点统一挂载 `/api/plugins/{pluginCode}/**`；插件自有 Thymeleaf 模板放插件 JAR 的 `src/main/resources/templates`，经插件作用域渲染器按逻辑名渲染。
- Java `Long`/Snowflake ID 在 JSON、插件 DTO、TS 模型、表单与 URL 参数中一律使用 `string`，禁止 `Number(id)`。
- 生产插件前端只通过 JAR 内 ESM `remoteEntry.js` + 宿主注入 SDK 加载；前端资源打进 `META-INF/yudream-plugin/frontend/{pluginCode}`。workspace 加载仅是开发便利，禁止生产 manifest 依赖 workspace 别名。

## 5. AI/Agent 与 CMS 要点

- AI 配置必须 provider-first 列表结构（`providers` 数组，每个 provider 持有 base URL、key、代理、默认模型与模型清单）；前端传 `providerCode` + `modelCode`。
- Spring AI 集成使用原生 tool calling，模型-工具交互不要求模型手工产出项目自定义 `toolCalls` JSON；`AiAgentTool` 为项目抽象，infra 负责适配。
- SSE 必须端到端真流式；事件使用可扩展信封（`ai.message`/`ai.tool`/`ai.result`/`ai.error` 等，含 `event/action/module/traceId/timestamp/payload`）。发送结构化 `ai.error` 后正常 complete，不得再 `completeWithError`。
- CMS 改动必须是完整发布闭环：权限菜单、管理路由、公开路由与渲染、发布/下线、SEO、页面/模板元数据，以及 GrapesJS 可视化构建兼容存储（`htmlContent`/`cssContent`/`builderProjectJson`）。

## 6. Excel / 导入导出

- 使用 EasyExcel；HTTP 文件读写放 `ExcelHttpSupport` 等支撑类，行映射与模板行创建放接口 assembler。
- 前端导出用 blob，并使用 `src/utils/excel.ts` 的既有工具；新增 `v-auth` 按钮时同步菜单种子枚举权限。

## 7. 前端组件与主题

- 前端实现优先复用项目既有的 `Fa*`、`Yd*` 组件和既有组合式能力；仅在现有组件无法覆盖明确需求时才引入 Arco 原生组件或自定义控件，并说明原因。
- 不在业务页面自行指定主色、品牌色或固定色板；主题、主色、深浅模式由后台主题配置与组件体系统一控制。业务样式仅使用中性语义变量（如 `--color-bg-*`、`--color-text-*`、`--color-border-*`、`--color-fill-*`），不直接模拟主题组件的激活/强调态。

## 8. 构建与验证

- 环境：JDK 21、Maven 3.9+、Node.js 22.22+/24.15+、pnpm 11.9+；Windows 下跑 Maven 需显式设置 JDK 21 环境，否则会误用 JDK 17 报 `invalid target release: 21`。
- 后端改动至少执行受影响模块的定向测试或编译：
  `mvn -pl <模块> -am -Dtest=<定向测试> -Dsurefire.failIfNoSpecifiedTests=false test`
- 前端改动执行目标 app 的 typecheck/build：
  `pnpm --dir yudream-frontend --filter @fantastic-admin/core-arco-design-vue run test:typecheck`
- 完成后端工作前按 skill 的 Review Checklist 执行 Controller 与乱码定向扫描。

## 9. Git 工作流

- 每个可独立使用的模块完成后，先运行对应验证，只暂存该模块文件并创建中文提交（如 `feat: 完成用户管理模块`），再进入下一模块。
- 改动或修正架构规则时，必须在同一提交同步更新对应 skill/`knowledge.json`，禁止代码与项目规则脱节。
- 未经用户明确要求不执行 push/tag 等外部动作；不纳入 IDE 元数据、临时文件等与任务无关的改动。

## 10. 契约发布（SPI / SDK / components）

- 只发布到 `nexus.yudream.online`；版本号永远从源文件读取，不凭记忆。
- 顺序：先在本仓升级并验证契约版本 → 提交推送 → tag 流水线发布并等待 verify 通过 → 再到 `yudream-admin-plugins` 同步依赖、刷新 lockfile、运行下游校验并单独提交。
- SPI 版本在 `yudream-plugins/yudream-plugin-spi/pom.xml` 与本仓根 `pom.xml` 的 `yudream.plugin.spi.version` 同步维护；下游只改其根属性。SDK/components 版本在 `yudream-frontend/packages/*/package.json`，下游经 `pnpm-workspace.yaml` catalog 消费并刷新 `pnpm-lock.yaml`。
- 发布/验签 CI：`publish:maven-plugin-spi`、`publish:npm-plugin-sdk`、`publish:npm-components`、`verify:maven-plugin-spi`、`verify:npm-contracts`；发布前跑 `validate:contract-packages`、`validate:contract-package-tarballs`、`validate:contract-publish-pipeline`。
- 未验证已发布的版本不得用于下游；未明确要求时不做发布后 snapshot 升版。

## 11. 完成前自检

- 分层归属是否正确？Controller/assembler 是否触碰了禁项？
- dataobj、Spring Bean、宿主内部类是否泄漏给了插件或接口层？
- 平台能力是否遵守双闸门与依赖级联？长 ID 是否全程字符串？
- 插件 JAR 的 `plugin.yml`、前端 remote 产物是否完整？软依赖路径是否条件注册？
- 是否完成目标构建/测试与定向扫描，中文文案无乱码，并同步了受影响的 skill/文档？
