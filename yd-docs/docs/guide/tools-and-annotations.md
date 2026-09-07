# 独有工具与注解

YuDream Admin 在通用后台能力之外，提供了一系列框架独创的工程工具与机制。本页是它们的索引，细节见对应章节。

## 通用接口层工具

接口层的通用基础设施（统一响应、长 ID 序列化、分页、Excel、请求日志链），逐项深挖见 [通用工具](/reference/toolkit)：

- **统一响应 `Result<T>` / `ResultCode`**：所有 Controller 返回统一包装，`code`/`message`/`data`/`timestamp`；静态工厂 `ok(...)` / `fail(...)`，`fail(String)` 固定 `code=1000`。
- **长 ID 序列化（`JacksonConfig`）**：为 `Long` 与 `long` 注册 `ToStringSerializer`，所有 Snowflake 主键在 JSON 中输出为字符串（如 `{"id": "1928347561029384756"}`），规避 JS `Number.MAX_SAFE_INTEGER` 精度丢失；前端与插件侧禁止 `Number(id)`。
- **分页基类 `PageBaseRequest`**：`page`（默认 1，`@Min(1)`）+ `size`（默认 10，1–100），业务 Query 继承即得边界校验；导出等大数据量由后端服务端放大，不开放给前端。
- **请求日志链**：`ContentCachingRequestFilter`（HIGHEST_PRECEDENCE，仅缓存 JSON/表单/text 类请求体）→ `WebInvokeTimeInterceptor`（耗时日志 + `ApiLogDTO` 脱敏入库，敏感键正则打码、body 2000 字符截断）→ `WebLogConfigure` 注册，开关与前缀由 `yudream.web.log.*` 控制。

## 宿主系统注解

宿主在 `yudream-domain` 定义的三组运行时注解，逐项属性表与处理器源码见 [系统注解详解](/reference/annotations)：

| 注解 | 用途 | 运行期处理器 |
|---|---|---|
| `@Cache` / `@RefreshCache` / `@DeleteCache` | 方法级两级缓存（Redis L2 + Caffeine L1），key/condition 支持 SpEL | `CacheAspect` |
| `@MenuModule` / `@MenuNode` | 菜单种子声明在枚举类/常量上，启动时同步入库并绑定系统角色 | `MenuEnumScanner` + `SystemMenuInitializer` |
| `@PermissionRegister` | 方法级权限元数据自动注册（与 `@SaCheckPermission` 配合） | `PermissionRegisterBootstrap` |

## 插件 SPI 注解

插件入口类实现 `YuDreamPlugin` 接口，元数据与静态贡献项可用 `online.yudream.base.plugin.spi.annotation` 包下的注解声明（宿主启动扫描后转为对应注册，等价于在 `onLoad` 中手工调用 `PluginContext.registerXxx(...)`）：

| 注解 | 标注位置 | 作用与关键属性 |
|---|---|---|
| `@PluginSpec` | 入口类 | 插件元数据：`code`/`name`/`version`/`description`/`dependencies`（硬依赖 code 列表）。未标注且未重写 `descriptor()` 时加载报错 |
| `@PluginCapability`（可重复，容器 `@PluginCapabilities`） | 类 | 声明插件能力项：`code`/`name`/`type`/`description`/`icon`/`defaultConfig`（`@PluginConfigEntry` key-value 数组）/`dependencies` |
| `@PluginMenu`（可重复，容器 `@PluginMenus`） | 类 | 宿主导航菜单：`title`/`path`/`icon`/`permission`/`parentPath`/`sort` |
| `@PluginPermission`（可重复，容器 `@PluginPermissions`） | 类 | 权限项：`code`/`name`/`module`/`description` |
| `@PluginDashboardCard`（可重复，容器 `@PluginDashboardCards`） | 类 | 工作台卡片：`code`/`title`/`component`（默认 `ACTION_CARD`）/`actionPath`/`tone`/`defaultW`/`defaultH`/`minW`/`minH`/`sort`/`defaultOnFirstVisit`（首次访问首页默认指引，用户保存布局后不再展示） |
| `@PluginFrontend` | 类 | 前端 remote 模块：`moduleName`/`entry`/`sdkVersion`/`integrity`/`menuTitle`/`menuIcon`/`menuSort`/`parentCode`，内嵌 `@PluginRoute[] routes()` |
| `@PluginRoute` | 仅作 `@PluginFrontend.routes` 元素（`@Target({})`） | 前端路由：`path`/`name`/`title`/`component`/`permission`/`sort`、父级三元组（`parentPath`/`parentTitle`/`parentIcon`/`parentSort`）、`hideInMenu`（保留路由但不在导航显示，用于带 ID 的上下文页）、`publicAccess`（匿名可访问，宿主注册为 `meta.public`，对应 HTTP 端点也须放开权限） |
| `@PluginHttpEndpoint` | 方法 | 声明挂载到 `/api/plugins/{pluginCode}/**` 的 HTTP 端点：`method`/`path`/`permission`/`wrapResult`（默认 true，自动包 `Result`） |
| `@PluginCommand`（可重复，容器 `@PluginCommands`） | 方法 | 消息命令：`code`/`command`/`name`/`permission`/`description`/`allowAnonymous` |
| `@PluginConfigEntry` | 作 `@PluginCapability.defaultConfig` 元素 | 能力默认配置项：`key`/`value` |

注解与编程式注册的关系、完整示例见 [插件注解声明](/plugin/spi/v1/annotations) 与 [插件核心契约](/plugin/spi/v1/core)。

## 插件 DevTools 浮动调试面板

宿主内置的插件开发调试工具（后端 `platform/devtools` 四层 + 前端 `plugin-devtools` 浮动面板），插件零适配：

- **dev-mode 热重载**：门控 `yudream.platform.plugin.dev-mode.enabled`（三态：缺省自动探测——源码运行开启、JAR 运行关闭；显式 true/false 优先）。dev 项目来自 yml 配置（CONFIG）或面板注册（FILE，默认 `plugins/dev-projects.json`，文件形式方便编码代理读取；设置页支持批量扫描子目录并去重）。watcher 将源码/编译产物/前端 dist 变更去抖为 **编译 → 级联禁用/卸载 → 加载 → 启用 → 前端 remount** 全链路事件；编译失败只发事件，绝不重载旧产物。
- **浮动面板**：根布局 FAB（可拖拽贴边、`Ctrl/Cmd+Shift+D` 唤起、Esc 关闭），Teleport 到 body 的非模态浮窗（不锁滚动、可拖动/缩放、记忆几何位置），五个页面：概览（状态卡片+生命周期流）、插件（资源总览）、追踪（Agent 执行链路）、审计（UI 规则审计）、设置（dev 项目管理）。SSE 桥广播 `plugin-lifecycle` / `agent-trace` 事件。
- 权限码：`platform:plugin-devtools:view` / `platform:plugin-devtools:manage`。

详见 `docs/plugin-system/dev-mode.md` 与 [插件开发](/plugin/overview)。

## 前端 UI 审计规则

`yudream-frontend/eslint-rules/` 内置两条自定义 ESLint 规则（均为 warn 级，不阻塞构建）：

| 规则 | 作用 |
|---|---|
| `yudream/prefer-fa-component` | 检测模板中的 `<a-*>` 与 `@arco-design/web-vue` 直接引用，提示改用共享 `Fa*`/`Yd*` 组件；其中 `Fa*` 来自 Fantastic-admin，`FaResponsiveTable` 是 YuDream 原创例外，`Yd*` 及其 composable 均为 YuDream 原创，不按 AI/非 AI 分类 |
| `yudream/no-brand-color-token` | 禁止业务代码使用 Arco `--primary-N` 色阶 token（主题系统自身的 `--primary` 除外），强制使用中性语义变量 |

运行 `pnpm audit:ui` 生成 `audit-report.json`，dev 中间件经 `/__yudream-devtools/audit.json` 提供给 DevTools 面板的审计页消费。

## Excel 导入导出体系

- 基于 EasyExcel；HTTP 文件读写收口在接口层 `ExcelHttpSupport`。
- 行映射与模板行创建放接口 assembler，Controller 不做模板构造。
- 前端工具 `src/utils/excel.ts`：`saveExcelResponse`（blob 导出保存）、`pickExcelFile`（选择文件）、`excelForm`、`importResultMessage`（导入结果提示）。
- 新增 `v-auth` 按钮时同步菜单种子枚举权限。

## 菜单种子同步三模式

`yudream.system.seed.menu.sync-mode`：

| 模式 | 行为 |
|---|---|
| `INIT_EMPTY` | 仅当菜单表为空时初始化 |
| `MISSING_ONLY` | 只插入缺失编码的种子菜单（生产推荐） |
| `OVERWRITE` | 全量覆盖同编码记录（开发期） |

策略判断在领域服务，基础设施层只读配置传入。

## 安全基线（system）

- **接口加密**：RSA+AES 载荷加密网关；登录前提供未加密状态端点与公钥端点，前端据此决定是否加密；SSE 入站可解密、出站流保持可流式。
- **双 token**：Sa-Token 登录态 + Redis 会话。
- **Passkey**：WebAuthn（yubico webauthn-server-core）。
- **OAuth / 外部登录**：自实现 OAuth client 网关 + 外部登录网关。
- **API Key**：独立 API Key 权限体系。

## Agent 执行追踪

`yudream.platform.agent.trace.enabled`（默认开启）：装饰 `AgentWorkflowRuntimeService.execute(...)`，对 chat/wiki/cms/debug/plugin 全入口追踪，无需调用方改动。追踪落 MongoDB（TTL 索引，默认保留 7 天 + 每源条数上限）；仅完成时落库，运行中通过 SSE 实时视图查看。

## AI 工具抽象与 SSE 信封

- `AiAgentTool`：项目级工具抽象（名称/描述/input schema/权限元数据），infra 适配 Spring AI 原生 tool calling；插件可经 SPI `registerAiTool` 注册自己的 Agent 工具。
- SSE 事件信封：`ai.message` / `ai.tool` / `ai.result` / `ai.error` / `ai.progress`，数据体统一含 `event/action/module/traceId/timestamp/payload`。
- AI 配置 provider-first：`providers` 数组，每 provider 持有 base URL、key、代理、默认模型与模型清单；前端只传 `providerCode + modelCode`。

## CMS 可视化建站闭环

GrapesJS 拖拽建站，存储三字段：`htmlContent`（发布 HTML）、`cssContent`（发布 CSS）、`builderProjectJson`（可再编辑的工程源）。一次 CMS 改动是完整发布闭环：菜单权限、管理路由、公开路由与渲染、发布/下线、SEO、页面/模板元数据。

## 渲染体系

- **render-server**：独立 Fastify + Playwright 服务，`/v1/render/html|markdown|url` → 图片。
- **插件模板渲染**：插件 JAR 内 `templates/` 目录的 Thymeleaf 模板，经 `context.templateRenderer()` 按逻辑名渲染，支持 CSS selector 元素级截图。
- **Word 模板**：POI 文档模板生成（`framework().wordTemplates()`）。

## 缓存与其他

- **两级缓存**：Redis（L2）+ Caffeine（L1），`yudream.cache.*` 支持空值过期与按前缀聚合指标。
- **雪花 ID 配置**：`snowflake.data-center-id` / `machine-id`；所有 Long/Snowflake ID 跨 JSON/前端边界一律 string。
- **插件商店**：Nexus 托管 `plugin-store-releases/index.json`，含 host/spi/frontend-sdk 版本兼容矩阵。
- **容器日志实时采集**：socket/CLI 双通道。
