---
name: yudream-ddd-architecture
description: Enforce YuDream Admin backend DDD layering and project-specific architecture. Use when modifying Java backend code, adding system modules, reviewing/refactoring controllers, assemblers, application services, domain aggregates, repositories, infrastructure implementations, plugin systems, remote plugin frontends, frontend plugin package decomposition, Excel/import-export code, permission/menu code, module-level git commits, Chinese commit messages, or when the user asks about DDD conventions, Controller responsibilities, UTF-8/Chinese string cleanup, or project architecture rules.
---

# YuDream DDD Architecture

## Mandatory First Steps

1. Read this skill before backend edits in this repository.
2. If the task touches a specific bounded context, inspect the matching packages in all four modules before editing:
   - `yudream-domain/src/main/java/online/yudream/base/domain/...`
   - `yudream-application/src/main/java/online/yudream/base/application/...`
   - `yudream-infrastructure/src/main/java/online/yudream/base/infra/...`
   - `yudream-interfaces/src/main/java/online/yudream/base/interfaces/...`
3. Prefer the `system/user` package as the baseline pattern. It is the reference for aggregate, value object, repository, application command/query/DTO/service, infra data object/mapper/impl/service, and interface controller/assembler/request/res layout.
4. Read `references/knowledge.json` when a task involves a repeated pitfall, known utility, or tool usage. Append durable lessons with `scripts/add_knowledge.py` when the user corrects the same issue, when a bug pattern is important, or when a reusable utility is discovered.
5. When a task proves an existing project rule is ineffective, changes an architecture decision, or introduces a reusable implementation pattern, update this skill or `references/knowledge.json` in the same work item. Do not leave code and project rules out of sync.

## Layer Contract

### Domain Layer

Use domain for business meaning and invariants.

Required package roles:
- `aggregate`: aggregate roots and entities such as `User`, `Role`, `Dept`.
- `valobj`: identity/value objects such as `RoleID`, `DeptID`, `UserDept`.
- `enumerate`: domain enums such as status, type, source.
- `repo`: repository interfaces only. No persistence implementation.
- `service`: domain services for cross-aggregate domain rules.

Rules:
- Keep framework/web classes out of domain.
- Put invariants and state transitions on aggregates when they belong to the aggregate.
- Use value objects for typed identifiers and meaningful compound values.
- Throw domain/business exceptions with normal Chinese text, not Unicode escapes or mojibake.

### Application Layer

Use application for use cases and orchestration.

Required package roles:
- `cmd`: write-use-case input objects, for example create/update/assign commands.
- `query`: read-use-case query objects.
- `dto`: application output DTOs.
- `assembler`: application-layer conversions between domain aggregates and application DTOs.
- `service`: application services. They orchestrate repositories, domain services, transactions, validation, and authorization-sensitive use cases.

Rules:
- Application services may construct aggregates and call aggregate methods.
- Application services must not return interface `res` objects and must not accept interface `request` objects.
- Do not bury large `toDTO`, `toDomain`, or row-mapping methods inside services. Move conversion to application assembler when it is not trivial.
- Keep persistence-specific query construction out of application.

### Infrastructure Layer

Use infrastructure for technical adapters.

Required package roles:
- `dataobj`: persistence objects such as Mongo DOs.
- `mapper`: DO/domain conversion.
- `impl`: repository implementations.
- `service`: external technical services and gateways.
- Additional adapter folders are allowed when they are explicit, for example `bootstrap`, `context`, scanner/config packages.

Rules:
- Repository implementations implement domain repo interfaces.
- Data objects never leak to application or interface layers.
- Mapper methods belong in infra `mapper`, not controller or application service.
- External systems such as S3, Redis, Sa-Token gateway details, mail, RabbitMQ, and Neo4j live here behind domain/application contracts.

### Interface Layer

Use interface for HTTP/API boundary only.

Required package roles:
- `controller`: HTTP routing, auth annotations, validation trigger, app-service calls, response wrapping.
- `assembler`: request/res/Excel row/web boundary conversion.
- `request`: inbound request bodies.
- `res`: outbound response bodies.
- `row`: Excel/import-export row models when needed.
- `vo`: allowed only for UI-specific view objects already established by the project; prefer `res` for HTTP responses.

Controller hard rules:
- No `new XxxCmd`, `new XxxRes`, `new XxxExcelRow`, or response `builder()` in controllers.
- No `private toXxx`, `parseXxx`, `templateXxx`, `rowToXxx`, or Excel template construction in controllers.
- No stream mapping from request rows to commands in controllers. Wrap that in an assembler method.
- No business invariants in controllers. Delegate to application/domain.
- Controller is allowed to set page/size limits, call auth/session helpers, pass app-service return values through an assembler, and return `Result`.

Assembler hard rules:
- Interface assembler converts `request -> cmd`, `domain/app DTO -> res`, `Excel row -> cmd`, `DTO -> Excel row`, and template rows.
- Application assembler converts `domain -> application DTO` and similar application-internal transformations.
- Infrastructure mapper converts `domain <-> dataobj`.

## Platform Capabilities

- Security and identity abilities such as interface encryption, dual token, API Key, Passkey, and OAuth are system baseline abilities and belong in `system`.
- API encryption must expose an unauthenticated and unencrypted status endpoint before the public-key endpoint, so frontend clients can decide whether to encrypt before login. The encryption filter must explicitly bypass both status and public-key bootstrap endpoints.
- API encryption may decrypt requests for `text/event-stream` endpoints, but must not wrap or encrypt the streaming response body; SSE responses must stay streamable while the inbound JSON request can still be encrypted.
- Optional engineering abilities such as SSE, WebSocket, MQ, Neo4j, Python Runtime, HTTP integration, document generation, CMS, and AI/Agent tooling are dynamically loadable platform abilities and belong in `platform`, separate from `system`.
- Platform capability runtime has two gates:
  - project gate: configuration file or Spring conditional annotation decides whether the project allows loading the capability provider, for example `yudream.platform.capabilities.rabbitmq.enabled=true` with `@ConditionalOnProperty`;
  - application gate: application service checks the persisted capability state before each use case and calls `ensureEnabled(...)` before invoking technical tools.
- A capability that is not allowed by the project gate must not appear as a runtime-toggleable provider, must not register endpoints/handlers, and must not be restored at application startup.
- A capability disabled by the application gate must reject use cases in the application layer and must not create external connections, declare middleware resources, or require projects to configure unused middleware.
- Infra providers are only tool wrappers. Provider construction and `enable(config)` may store local config or mark state, but must not open MQ/Neo4j/Redis-like connections, verify remote connectivity, declare queues/topics, or start long-running resources.
- External connections/resources may be created only after both gates pass and a business operation, connect action, health action, or explicit test action actually needs the external system. Close and clear resources on disable.
- Platform capabilities must declare runtime dependencies in `CapabilityDescriptor.dependencies`. Application services must reject enabling a capability when any dependency is unavailable or disabled, and disabling a dependency must disable dependent capabilities so the UI and runtime state remain truthful.
- AI/Agent capability integrations should expose reusable agent tools instead of hard-coding one-off page mutations. A tool must declare its name, description, input schema, and permission metadata; application services execute tool calls and stream structured tool results through dedicated SSE events such as `tool`, while chat `delta` events remain natural-language content only.
- AI model configuration must be provider-first and list-shaped. Store AI runtime providers in a `providers` JSON array, where each provider instance owns its API base URL, API key, proxy, default model, chat models, embedding models, rerank models, and provider type such as `OPENAI`, `OPENAI_COMPATIBLE`, `KIMI`, or `DEEPSEEK`. Frontends should pass `providerCode` plus `modelCode`; infrastructure provider adapters handle provider/model-specific request parameters such as `thinking`, `reasoning_effort`, `extraBody`, and non-standard response behavior. Do not model multi-provider AI settings as a flat dictionary of `baseUrl/apiKey/model/models`.
- AI plumbing is owned by AgentScope Java (`io.agentscope:agentscope-harness`). Never hand-roll AI low-level logic in the framework: model instance construction/connection reuse (`AgentScopeModels`), stream delta parsing, the tool-calling loop, structured output with degradation, and retries must go through the framework. Spring AI has been removed from the project.
- Provider differences are expressed by `AiProviderAdapter.generateOptions(...)` producing AgentScope `GenerateOptions` (`additionalBodyParams` for Kimi/DeepSeek `thinking`, `toolChoice`, `reasoningEffort`). Keep YuDream `AiAgentTool` as the project/domain abstraction and adapt it to AgentScope `ToolBase` in infrastructure (`AgentToolAdapter`/`PluginAiToolAdapter`); never ask the model to manually emit project-specific `toolCalls` JSON.
- Stateful conversations must run on the Harness runtime (`HarnessAgentRuntime`: one `HarnessAgent` singleton per agent code, MongoDB `AgentStateStore` + `BaseStore` via `MongoDistributedStore`, `IsolationScope.USER`). Conversation context, compaction, and two-layer long-term memory are framework-owned via `AgentState`; the platform message table is only a UI projection — do not trim history by hand. Chat agents must disable host filesystem and shell tools. Stateless one-shot calls go through `AgentScopeGenerationGateway` (domain port `AiGenerationGateway`).
- Per-call customization rides on `RuntimeContext` + middleware: `PerCallModelMiddleware` (per-request provider/model selection), `PerCallSystemPromptMiddleware` (per-request system prompt override; agent input message lists must never contain SYSTEM messages — system prompts go through `sysPrompt`/`onSystemPrompt`), `PerCallToolFilterMiddleware` (schema-level tool filtering by permission snapshot). Model-visible tool names must come from `ToolSchemas.safeName`.
- Tool permission control is four-layer: schema filtering by permission snapshot → `PermissionContextState` rules/mode → execution-time fail-closed re-check in `AgentToolAdapter.callAsync` (snapshot from `RuntimeContext`, `AgentToolPermissions`) → sandbox execution guard last. Plugin DESTRUCTIVE/confirmation-required tools must return permission ASK (HITL).
- Skills are managed at framework level: `platformAgentSkill` via `MongoAgentSkillRepository implements AgentSkillRepository` feeds every Harness Agent's `<available_skills>`; admin endpoints live under `/api/platform/agent-skills` with view/create/edit/delete permission codes.
- AI/Agent streaming endpoints must be streaming end to end: the HTTP SSE controller, application callback, and infrastructure model call should all propagate natural-language deltas (AgentScope `TextBlockDeltaEvent`/`ThinkingBlockDeltaEvent`) instead of wrapping a blocking `call()` as fake streaming. Match model read timeouts to the SSE timeout for long-running tool generation.
- Framework tool-calling streams may not emit natural-language deltas while the provider is planning tool arguments. For user-facing builders, expose a domain/application progress callback, send `ai.progress` milestones such as `analysis`, `subscribed`, `tool-start`, `tool-complete`, and send tool results immediately when callbacks execute instead of waiting for the final model summary.
- All AI frontends use public YD Agent/chat components. Render only actual reasoning in the reasoning panel; keep natural-language deltas, tools and lifecycle progress separate. Do not import TDesign Chat directly in business pages.
- Only real model reasoning, for example OpenAI-compatible `reasoningContent` / `reasoning_content`, should be rendered as YD reasoning; lifecycle progress and heartbeat events must remain separate and must not be merged into the thinking panel.
- YD Coding Agent 工作台是受控 UI 投影：运行、权限、上下文压缩与持久化仍由宿主 Harness 管理。回答内容采用 version 1 的 markdown/reference/tool 渲染块，引用原文、文件位置与工具状态独立于推理；入口先校验协议并局部清理 Markdown HTML，引用外链仅 HTTP(S)，文件引用通过受控事件交宿主。Skills、MCP、工作区与审批控件不得宣称不存在的端到端 API；演示明确标识本地状态。详见 `docs/yd-agent-workbench.md`。
- 宿主与插件 Vue Web 业务只使用 `@yudream/components` 的公开 `Yd*` / `useYd*`。禁止 Fa/useFa、裸 Arco/Reka/TDesign、别名绕过和自造通用交互；缺能力先封装 YD，再同步导出、resolver、SDK桥、预览、文档与严格检查。原生内容/布局元素允许，技术编辑器通过 YD adapter 承载；第三方与 Fa 只在共享组件内部及明确的框架启动装配边界使用。主色由 FA 主题引擎统一管理，页面仅用中性语义变量。完整视觉、动效、流程、树、集合/弹出选择与浮层选型见 `docs/yd-ui-guidelines.md`，该规范与宿主/插件 AGENTS/skill/目录在组件变更的同一工作项实时同步维护，不以规则禁用或未发布契约绕过。
- CMS builder should enable deep-thinking behavior at the backend/application boundary by default; do not expose a separate deep-thinking toggle in the builder sender UI unless product requirements explicitly reintroduce it.
- CMS AI tool status, payloads, cursors, and business context are structured request/runtime metadata. Render them through `message.tools` / `message.activities` and keep them out of visible assistant `content`; never serialize `[工具上下文]` or similar internal markers as an assistant history turn, because models can echo it into user-visible answers and persisted sessions.
- CMS canvas CSS editing and AI CSS tools must preserve selector and at-rule semantics. Edit related rules by replacing them in the complete GrapesJS CSS, and merge incremental CSS by normalized selector within the same at-rule path with later declarations overriding earlier ones. Do not append raw CSS strings or use substring detection for deduplication; Grid/Flex children that may contain long or nowrap content need `min-width: 0`, and flexible grid tracks should prefer `minmax(0, 1fr)`.
- CMS AI builder chat history should be scoped by editing target, for example `home:home` or `page:{id}`, and loaded lazily on the frontend. Store only lightweight summaries in lists, load full messages on demand, and avoid persisting large base64 attachments in history records.
- General agent tools such as `web.fetch` are allowed when they are declared as `AiAgentTool`, have permission metadata, and are used for analysis before mutation tools. Mutation tools such as `cms.canvas.patch` should remain the only tools that directly change frontend state.
- Some OpenAI-compatible gateways return JSON with non-standard response content types such as `application/octet-stream`. AgentScope's OkHttp transport tolerates this; if a future transport change reintroduces strict content-type parsing, handle the mapping in the transport layer, not by post-processing model responses.
- AI/Agent SSE events must use an extensible envelope instead of naked payloads. Use semantic event names such as `ai.message`, `ai.tool`, `ai.result`, and `ai.error`; the data body must include `event`, `action`, `module`, `traceId`, `timestamp`, and `payload`. Put text in `payload.content`, tool results in `payload.tool`, final results in `payload.result`, and errors in `payload.message`. Do not emit raw model JSON as chat content.
- Long-running AI/Agent SSE endpoints should send an immediate `ai.progress` event after accepting the request, before calling the model. Also send progress before expensive tool application so the frontend never appears idle while the model is planning tool arguments or the tool is mutating the canvas.
- After an SSE endpoint has sent a structured `ai.error` event, complete the emitter normally instead of calling `completeWithError`; otherwise Spring MVC may try to route the exception through global JSON handlers while the response content type is already `text/event-stream`.
- CMS platform work must be a complete publishing loop, not only backend CRUD. A CMS change should include backend menu permissions, admin route/menu registration, public frontend routes, public rendering, publish/unpublish flow, SEO fields, page/template metadata, and visual-builder-compatible content storage such as saved HTML plus Markdown fallback.
- CMS visual editing uses GrapesJS on the frontend. Store the published HTML in `htmlContent`, the published CSS in `cssContent`, and the editable GrapesJS project source in `builderProjectJson`; homepage builder data lives in `HomePageLayout.settings.homeHtml`, `homeCss`, and `homeProjectJson` unless a dedicated backend aggregate field is later added. Keep Markdown/HTML editing and legacy homepage sections as fallback modes, but do not keep a second visual-builder dependency or old visual-builder component tree after migrating to GrapesJS.

## Plugin Architecture

- `yudream-plugin-spi` is the only compile-time contract module that third-party plugin JARs may depend on. Plugin code must not depend on `yudream-domain`, `yudream-application`, `yudream-infrastructure`, `yudream-interfaces`, or bootstrap implementation classes.
- Plugin source modules live under `yudream-plugins/`, for example `yudream-plugins/yudream-plugin-spi`, `yudream-plugins/yudream-sample-plugin`, and `yudream-plugins/yudream-plugin-{code}`. Maven `-pl` commands should use those paths. Runtime plugin JARs may still be placed directly in the `plugins/` directory.
- Package a `plugin.yml` at the JAR root as the authoritative plugin manifest. It must declare `name`, `main`, and `version`; `name` is the stable plugin code, while optional `displayName` is the user-facing localized name and falls back to `name`. Dependencies and service lookup must always use `name`, never `displayName`. Use `depend` for hard provider-plugin dependencies and `softdepend` for optional provider-plugin dependencies. Plugin runtime instantiates `plugin.yml.main` directly; do not package `META-INF/services/online.yudream.base.plugin.spi.core.YuDreamPlugin` or rely on `ServiceLoader`. Prefer annotation-driven registration for static permissions, menus, frontend routes, HTTP endpoints, capabilities, and migrations. Imperative `PluginContext.registerXxx(...)` calls are allowed for dynamic runtime contributions and conditional registrations.
- Keep plugin projects engineered by package responsibility. Do not place real plugin functionality in a single `YuDreamPlugin` class. A plugin with business behavior should split into packages such as `domain`, `application`, `infrastructure`, `interfaces`, `migration`, `frontend`, and `bootstrap` according to its size. The plugin entry class should describe and wire the plugin, not contain business workflows, database migration code, HTTP business logic, or UI route construction.
- Spring-related plugin projects must follow the same DDD principles as host modules. Use aggregate/value-object/repository contracts in domain; cmd/query/dto/assembler/service in application; dataobj/mapper/impl/service in infrastructure; controller/assembler/request/res in interfaces. Plugin HTTP handlers and Spring controllers must delegate to application services and must not inline request-to-command mapping or business invariants.
- Plugin HTTP endpoint methods belong in `interfaces/controller`, not on the `YuDreamPlugin` entry class. Use `PluginContext.registerHttpController(new XxxController(...))` from `onEnable` after wiring application services. The entry class may keep static manifest annotations and runtime composition only.
- Keep the SPI module engineering package layout by responsibility:
  - `online.yudream.base.plugin.spi.core`: plugin descriptor, lifecycle, and runtime context;
  - `online.yudream.base.plugin.spi.http`: generic plugin HTTP handler request/response contracts;
  - `online.yudream.base.plugin.spi.frontend`: frontend module, remote entry, and dynamic route metadata;
  - `online.yudream.base.plugin.spi.menu`: plugin menu contribution metadata;
  - `online.yudream.base.plugin.spi.permission`: plugin permission declarations;
  - `online.yudream.base.plugin.spi.capability`: optional platform capability declarations;
  - `online.yudream.base.plugin.spi.system`: stable host framework capability ports exposed to plugins.
- Plugin JAR entry classes implement `core.YuDreamPlugin`. All menu, permission, capability, frontend, and HTTP registrations must happen through `core.PluginContext` so hot unload can remove every runtime contribution. A provider may expose its own business interface with `context.exposeService(Api.class, implementation)`; this generic lifecycle mechanism is not a host business port.
- Generic extension points use `PluginContext.registerExtension(ExtensionPoint.class, implementation, priority)`; consumers query through `PluginContext.extensions(...)` or the host-side `system.extension.PluginExtensionQuery` port. The extension-point interface may be host-defined (for example the `system.auth` contracts) or live in a provider plugin's `*.api` package. Lower priority values run first (default 0, ties by registration order); registrations are recycled automatically on disable/unload, so extension pipelines never retain implementations from disabled plugins. Every extension point must document its failure strategy: veto-style points (register/login interceptors) are fail-closed, notification points (`AuthEventListener`) are fail-open. Never use reflection into host internals as an escape hatch.
- Plugins may call YuDream framework abilities only through SPI ports such as `system.FrameworkServices`, `system.user.PluginUserService`, and `system.security.PluginSecurityService`. If a new host ability is needed, add a stable SPI port/DTO first, then implement the adapter in the host application/runtime layer.
- Plugin-owned Thymeleaf image templates belong under `src/main/resources/templates` in the plugin JAR. Render them through the plugin-scoped `PluginContext.templateRenderer()` using a logical name without `.html`; the runtime must bind resolution to that plugin's ClassLoader, reject traversal/absolute paths, and use native element screenshots when a selector is supplied. Do not place plugin templates in the host framework templates directory.
- SPI exposes only lifecycle contracts, contribution metadata, and stable host framework capability ports. Plugin-owned business APIs, DTOs, and service interfaces belong in the provider plugin, which may package its `*.api` package in the same JAR as its implementation. Consumers declare the provider in `plugin.yml`, compile against that API with `provided` scope, and use `context.service(providerCode, Api.class)` for direct in-process calls. Never package a duplicate copy of a provider API in the consumer JAR.
- When a provider plugin intentionally owns a multi-implementation extension API, dependent plugins may each `exposeService(ProviderApi.class, implementation)` and the provider may discover active implementations through `context.services(ProviderApi.class)`. The provider API still lives in the provider JAR; do not move it to host SPI.
- Hard dependencies must load and enable before their consumer. Persist both `depend` and `softdepend` from `plugin.yml`; during startup restore, recursively restore every enabled soft dependency before creating its consumer class loader, while missing, disabled, or failed soft providers must not block the consumer. A consumer must check `context.dependencyAvailable(code)` or the optional service result and must not register/show/enable features that need an unavailable provider. Soft-dependent menus, routes, endpoints, permissions, and tasks must use conditional `PluginContext` registration rather than unconditionally scanned annotations. A provider cannot be unloaded while any hard or soft dependent remains loaded; unload dependents first. The `depend`/`softdepend` graph must be acyclic: a platform adapter must not softdepend a business plugin that already softdepends the adapter (launcher-adapter must not list minecraft-server; server-list pages and OAuth scopes live on minecraft-server via `LauncherProvider`). Host reload depth sorting has a cycle guard only as a safety net.
- Do not expose domain aggregate roots, domain repositories, application services, infrastructure repositories, MyBatis/Mongo data objects, or Spring beans directly to plugin code. Domain invariants still belong in the host domain/application layers; plugins receive DTO-style views and invoke use-case-oriented ports.
- Plugin-facing DTOs must be stable, serialization-friendly records or simple value objects. They should not reuse interface `request/res` classes or persistence `dataobj` classes.
- Java `Long` identifiers, especially Snowflake IDs, must cross JSON/plugin/front-end boundaries as strings. Plugin DTOs/responses/requests, TypeScript models, form state, URL params, and SDK calls must not expose those IDs as `number` or call `Number(id)`, because JavaScript loses precision above `Number.MAX_SAFE_INTEGER` and follow-up saves/selects will query the wrong record. Keep domain/application storage as `Long` when appropriate, but convert to `String` at the API boundary and parse back only inside backend application services.
- Plugin HTTP endpoints should be mounted under plugin-scoped paths such as `/api/plugins/{pluginCode}/**` through a runtime dispatcher for the first version. Avoid dynamic Spring MVC controller registration until unload semantics, permissions, and diagnostics are proven stable.
- Frontend plugins should use the host-provided dynamic frontend SDK/client instead of bundling a private axios instance. SDK version and remote entry metadata must be part of the frontend manifest so the host can refresh or replace SDK behavior without rebuilding every plugin.
- Frontend plugin pages must not be implemented as host `apps/*/src/views` business pages except for temporary compatibility shims. Embedding plugin business pages in the host frontend is treated as the same coupling as not using plugins. For official business plugins, the default source location is a standalone plugin repository such as `yudream-admin-plugins`, not the core repository. Local core workspaces may keep only shared packages such as `@yudream/plugin-sdk`, `@yudream/components`, and temporary migration shims; do not add new official business plugin source under core `yudream-frontend/packages/plugin-*`. Production plugins must expose an ESM `remoteEntry.js` from the plugin JAR. For the standard JAR layout, omit `@PluginFrontend.entry` and let the runtime default it to `/api/platform/plugins/{pluginCode}/assets/remoteEntry.js`. Host runtime pages load the remote module and inject SDK/route props.
- Plugin frontend assets must be packaged under `META-INF/yudream-plugin/frontend/{pluginCode}` inside the plugin JAR, usually by copying `yudream-frontend/packages/plugin-{code}/dist` from the plugin module `pom.xml`. The runtime maps browser requests `/api/platform/plugins/{pluginCode}/assets/{assetPath}` to that classpath directory. Only set `entry` explicitly for non-standard external hosting or a non-standard entry file.
- Workspace package loading is only a local development convenience. Do not write production manifests that import workspace aliases such as `@yudream/plugin-blessing-skin`, and do not require the host app to depend on a specific plugin frontend package. The production contract is the remote ESM entry plus SDK.
- Official business plugin backends should also live in the standalone plugin repository by default. The core repository should keep `yudream-plugin-spi`, sample/demo plugins, host runtime code, and shared contracts, but must not add new official business plugin modules back into `yudream-plugins/` after the split.
- Frontend plugin packages must be engineered by responsibility instead of placing a whole plugin in one `.vue` file. Use at least `src/pages` for route pages, `src/components` for reusable UI, `src/composables` for stateful workflows, `src/api` for SDK-backed API wrappers, and `src/types.ts` for plugin view models. A top-level plugin shell may only select the current page and provide shared state; large forms, lists, dashboards, migration tools, repeated panels, and route-specific layouts must be split into pages/components.
- A frontend plugin route should map to a real page component, not to a giant tabbed component that hides all features inside one route. When a plugin has multiple management surfaces, define separate route entries and keep each page small enough that page logic, reusable panels, API calls, and types are independently testable.
- Plugin frontend declarations retain module menu title, icon, and sort, with separate routes for distinct management surfaces. New plugins must declare `plugin.yml` `menu.category` using their dominant business category, for example `mc` (MC), `todo` (待办), `social` (社交), or `other` (其他); custom categories use a stable code or `{code,name,icon,sort}`. A present `menu` block defaults to `CATEGORY`: share a host-owned root `plugin-category:{code}` and place plugin modules/functions below it. First-level large icons represent categories. Explicit `parentCode` takes precedence; `menu.mode: INDEPENDENT` is an explicit exception, and absent `menu` or `LEGACY` retains old plugin behavior. Keep existing persisted/admin-edited menus unchanged; categories have no route or permission and are not owned/disabled by any one plugin. Menu management defaults new roots to `CATEGORY` while allowing explicit legacy/exception types. Frontend menu/matcher projection must preserve both pathless category groups and legacy path-bearing root menus/links. See `docs/plugin-system/specification.md` section 10.
- SITE theme plugins may declare Vue native pages: `@PluginTheme(homeComponent="theme/Home", chromeComponent="theme/Chrome")` names remote-module route keys (`[a-z0-9][a-z0-9/-]*/[A-Za-z][A-Za-z0-9]*`). `homeComponent` takes over `/site`; `chromeComponent` takes over public-site header/footer (host injects navigation data and always prepends a Home `/site` item, the theme paints chrome so page switches never restyle the bar). Public wiki `/wiki` and embed `/embed` use the same chrome, including the default theme. Remaining pages use `@PluginRoute(publicAccess=true, siteNav=true)`. Themes must `softdepend` business plugins, never `depend`. Without `chromeComponent` the host SiteChrome remains and may still consume the chrome CSS-variable contract. Splash chrome is also plugin-owned: host `loading.html` stays the generic animation and must not ship theme GIF/fonts in `public/`; a SITE theme restyles `[data-app-loading]` from its own assets. `index.html` injects the last cached SITE CSS on public paths (including guest `/`) before Vue starts, and `theme-runtime` opens SITE stylesheet media from the URL public-path prefix before Vue Router is ready. Details: `docs/plugin-system/specification.md` 9.1 and `references/knowledge.json` keys `plugin-theme-vue-pages` / `site-chrome-var-contract` / `plugin-theme-splash` / `site-wiki-chrome-embed`.
- Runtime implementations must track disposables, classloaders, registered handlers, frontend manifests, menus, permissions, and capability providers by plugin code, and must release them on disable/unload.

## Plugin DevTools and Frontend Audit

- 后台外观预设属于宿主 `settings.appearance`，沿现有主题 JSON 设置持久化；共享 YD 组件与预览消费同一套 FA 主题变量和 `@fantastic-admin/themes/runtime` 主色转换。`@yudream/components/appearance.css` 在 `yudream-base` 层根节点声明 `--yd-*` 默认值，宿主 `admin-appearance.css` 仅导入；ADMIN 插件在 `:root` / `.dark` 覆盖，不得用内联变量或子布局默认值阻断继承。三套外观均保留已启用的功能动画；灵动完整，雅致与紧凑的系统及用户减少动态效果偏好仅柔化动作、速度与装饰密度，禁止统一冻结动画。公开 YD 入口需同步根导出、resolver 与 SDK host-components 共享桥，SPI 不增加主题字段。详见 `docs/frontend-appearance.md`、`docs/yd-components.md`。

- Developer tooling is host-side only and lives in `platform/devtools` (backend four layers) plus the `plugin-devtools` frontend store/floating panel; Agent execution tracing lives in `platform/agent`. Plugins need zero adaptation, and none of this touches `yudream-plugin-spi` contracts. See `docs/plugin-system/dev-mode.md`.
- Plugin dev mode is gated by `yudream.platform.plugin.dev-mode.enabled`, which is tri-state: unset means auto-detect via `DevModeEnvironment` (gateway class code source is a directory → source run → on; JAR → off), explicit `true/false` wins. Because `@ConditionalOnProperty` cannot see the auto-detected value, `PluginDevModeWatcher` decides in `@PostConstruct` whether to start its poll thread and must log a loud startup warning naming the gate source (auto/config). Dev projects load from `target/classes` + `target/plugin-dev/lib/*.jar` and win over same-code JARs; the watcher debounces source/compile-output/frontend-dist changes into compile, disable→unload→load→enable, and frontend-remount events. Compile failures are published as events and must never reload stale output or crash the host.
- Dev projects come from two sources merged by `PluginDevProjectCatalog`: CONFIG (yml `dev-mode.projects`, read-only in the panel) and FILE (panel-registered, persisted to a local manifest file — default `plugins/dev-projects.json` under `user.dir`, gitignored via `/plugins/`, overridable via `dev-mode.store-file`). FILE is deliberately file-based (not DB) so coding agents can read it to locate plugin sources. On same code CONFIG wins with a warning; the panel can only add/remove FILE entries; the manifest has an mtime cache and reloads automatically; registering a project for an already-enabled plugin publishes `PluginDevReloadRequested` immediately. Blank `code` is inferred from `<path>/target/classes/plugin.yml` then `<path>/src/main/resources/plugin.yml`, failing with a compile-first hint. Default `compileCommand` (single register blank, batch register, and `DevProject` field default) is `mvn -q compile -DskipTests -P dev-export`, so source-change auto-compile keeps `target/plugin-dev/lib` runtime JARs in sync; do not drop the profile when customizing the command unless the plugin has no runtime third-party dependencies.
- Agent tracing is gated by `yudream.platform.agent.trace.enabled` (default `true`) and decorates `AgentWorkflowRuntimeService.execute(...)` in the application layer so every entry (chat/wiki/cms/debug/plugin) is traced without caller changes. Traces persist to Mongo with a TTL index (`retention-days`, default 7) and per-source caps (`max-per-source`); step increments publish application events bridged to SSE. Traces are only persisted on completion, so running executions are visible only through the DevTools SSE live view. Historical traces are queried and analysed from the logs-menu page `system/agent-trace` (`GET/DELETE /api/platform/agent-traces`, Excel export) without requiring plugin-devtools or `requireDevMode()`; DevTools keeps a compact debug panel plus the live stream.
- DevTools HTTP endpoints mount under `/api/platform/plugin-devtools/**` with permission codes `platform:plugin-devtools:view`/`manage`. The SSE bridge (`PluginDevToolsSseBridge`) broadcasts `plugin-lifecycle` and `agent-trace` events; because sa-token reads tokens from headers only, frontends must consume these streams with fetch + ReadableStream, never `EventSource`. Lifecycle, agent-trace, and plugin-log SSE endpoints must call `PluginDevToolsAppService.requireDevMode()` so production JAR runs (dev-mode auto-off) reject the stream with a business 400 instead of holding emitters. The floating panel may load `/status` for permissioned users, but `connect()` and `onMounted` must require `status.devModeEnabled === true`; reconnect loops stop on 400/401/403/404/405 and cap transient network retries, otherwise production consoles flood `GET .../stream`.
- The floating debug panel (`src/layouts/components/PluginDevtools/`) is the only panel surface — no fixed admin page or menu seed. The FAB lives in the root layout (route-independent), is draggable with edge snap, collapses to a semi-hidden edge strip when dropped within 24px of a screen edge (hover brightens/widens it, clicking opens the panel in one step), and persists `{side, topRatio, docked}` in localStorage (`pluginDevtoolsFab`). The panel itself is a non-modal floating window Teleported to `body` — never a drawer: no overlay, no scroll lock, the system stays fully usable while it is open. It is header-draggable and corner-resizable, persists geometry `{left, top, width, height}` in `pluginDevtoolsPanel` with viewport clamping, toggles via `Ctrl/Cmd+Shift+D` and `Esc` (guarded against inputs and already-open modals), and uses a left icon nav with five pages — overview (status cards + lifecycle feed), plugins (master-detail assets), traces, audit, settings (dev-project management + FAB/panel reset) — persisting the active page (`pluginDevtoolsPage`). Z-index layering keeps it above everything: FAB/panel 2100 over sidebar 1010, header 1020 and host modals 2000; modals spawned inside the panel use 2200. Visibility requires `platform:plugin-devtools:view` plus `status.devModeEnabled === true`; a reachable status endpoint alone must not start SSE. Frontend remount after plugin reload uses a `?v=timestamp` entry URL and re-created asset nodes (state-resetting remount, not HMR).
- Frontend UI audit must enforce the YD-only policy rather than merely suggest Fa. Scan business templates, imports/aliases and render/JSX against the real public YD surface; preserve narrowly documented implementation/provider boundaries and do not allow per-line disables or broad exclusions to hide component gaps. Keep the development report shape (`generatedAt`/`summary`/`violations[]`) compatible. After migration reaches zero, retain an empty per-file baseline with the accepted commit SHA; never restore historical allowances or initialize from working-tree changes. Brand-color audit prohibits business token declarations and consumption. A verified plugin theme asset may declare tokens only in selectors permitted by its single declared scope: SITE needs a SITE anchor; ADMIN permits only `:root`/`.dark`. Consumption remains prohibited; dual-scope assets fail closed unless asset isolation is proven. Update the audit mappings and normative docs whenever a component changes; see `docs/yd-ui-guidelines.md`.
- Shared `YdSelect` must reflect the actual model: no first-option fallback for empty values, all selected labels inside the multi-select trigger, and optional local `allowSearch` filtering that preserves selection. Keep scalar/array values, groups, disabled items and string IDs compatible; use `YdTablePicker` for remote pagination. See `docs/yd-select-components.md` and verify the published contract before plugin adoption.
- Beware: `pnpm lint:eslint` runs with `--fix` at the repo root, and any `eslint.config.js` change invalidates the eslint cache, so a full run mass-rewrites unrelated files. Verify rule changes with targeted `npx eslint <paths> --no-cache` runs and only stage the files owned by the work item.

- 外部登录插件自动开户后绑定使用 SPI 2.29.0 的 `PluginExternalLoginIdentity.authenticatedUserId`（字符串 ID）。只有新建用户或已证明本站凭据的用户可返回此字段；不得按邮箱匹配或浏览器参数自动认领已有账号。宿主核销 state 后必须检查停用与绑定冲突，缺省字段保持 `BIND_REQUIRED`。开户前使用 `PluginUserService.findByExternalIdentity` 排除既有绑定，避免用户更改本站邮箱后被重复开户。详见 `docs/plugin-system/spi-2.29.0-external-login.md`。

- 插件第三方登录入口的呈现与排序使用 SPI 2.32.0（2.30.0 已由 `PluginDescriptor.gitUrl` 占用、2.31.0 已由插件 HTTP 上传契约 `PluginHttpPart` / `PluginHttpRequest.parts` 占用）：`PluginExternalLoginProvider.presentation()`（default 方法，`ICON` 缺省 / `TAB` 登录方式 Tab，宿主对未实现、返回 null 或抛异常一律回落 `ICON`）声明呈现方式；`descriptor().sort()` 同时对 Tab 与图标入口排序，内置 Tab 基线是账号密码 100、Passkey 200，因此 `sort < 100` 的插件入口排在账号密码之前。首选登录方式是站点级配置，存 `site` 分类通用 Setting 键 `loginDefaultMethod`（取值 `password` / `passkey` / `external:{providerCode}:{type}`，写入时非法值归一化为空串），随 `/api/settings/public` 匿名下发；登录页默认选中它，入口未启用、已下线或非 Tab 型时静默回落账号密码，第三方账号绑定流程（`externalLoginBindingToken`）忽略该配置。宿主登录页的账号输入框与「记住账号」行必须常驻（`v-show` 隐藏，不能用 `v-if`）：字段随 Tab 卸载会让浏览器自动填充与 vee-validate 注册失效，表现为「切回账号密码登录点按钮没反应」。详见 `docs/plugin-system/spi-2.32.0-login-tab.md`。

## Setup Installer and Bootstrap Configuration

- Fresh deployments with no database configuration boot into a minimal installer slice: `YuDreamApplication.main` delegates to `InstallerApplication` (restricted component scan over `interfaces.installer`, `application.installer`, `infra.installer`, `bootstrap.installer`). The slice excludes Mongo/Redis/Mail auto configurations and `cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate` (which unconditionally requires the Redis `StringRedisTemplate` bean), so the app starts with zero middleware.
- Installer-generated bootstrap configuration lives in `config/yudream-bootstrap.properties` (default under `user.dir`, override with `YUDREAM_BOOTSTRAP_CONFIG`; Docker mounts `./config:/app/config`). `BootstrapPropertiesEnvironmentPostProcessor` (registered in bootstrap `META-INF/spring.factories`, ordered right after config-data processing) adds it as the FIRST property source, so the file wins over `MONGO_URI`/`REDIS_*` environment variables; env remains the fallback for pre-installer deployments.
- The post-processor also injects `yudream.bootstrap.installer` (gates installer controller/service beans; normal mode never registers `/api/installer/**`) and `yudream.bootstrap.config-file` (resolved file location).
- Installer endpoints: `GET /api/installer/status`, `POST /api/installer/discover` (parallel probes over `YUDREAM_DISCOVERY_MONGO_URI`/`YUDREAM_DISCOVERY_MONGO_HOST`/`PORT`/`YUDREAM_DISCOVERY_REDIS_HOST`/`PORT` hints, compose service names `mongo`/`redis`, then localhost), `POST /api/installer/probe/mongo|redis`, `POST /api/installer/apply`. Apply re-probes, honors `YUDREAM_SETUP_TOKEN`, validates or auto-generates the Base64-32-byte `yudream.credential.key`, writes the file atomically, and publishes `InstallerConfigAppliedEvent`; bootstrap `InstallerExitListener` delays 1.5s then exits so the Docker restart policy reboots the container into normal mode (bare-metal dev restarts manually once).
- Probe ports live in `domain/installer` (`MongoProbe`, `RedisProbe`, `BootstrapConfigStore`) with driver implementations in `infra/installer`; probes create short-lived connections per call and never hold long-lived resources.
- EnvironmentPostProcessor property lookups must read raw values from property sources. `environment.getProperty("spring.data.mongodb.uri")` throws `PlaceholderResolutionException` when application.yml holds an unresolved `${MONGO_URI}` placeholder — exactly the fresh-install case the installer boots in. The same trap applies at RUNTIME to any `environment.getProperty` on keys whose yml value is an unresolved placeholder (e.g. `spring.mail.username` = `${MAIL_USERNAME}` with no default); guard such lookups everywhere.
- Runtime integration config (mail/S3/site URL) follows DB-first, env-fallback precedence: values persist in `sys_setting` (category `integration.mail` / `integration.storage`), secrets encrypt through `CapabilityCredentialCipher` with AAD `system.integration.{category}:{key}` and are never returned to the UI (`passwordSet` / `secretKeySet` flags only; blank secret on save keeps the stored value). `SystemIntegrationSettings` resolves effective values; `MailSenderProvider` / `StorageClientProvider` cache clients keyed by config hash and rebuild (closing old S3 clients) so admin edits apply WITHOUT restart. Candidate-config tests go through the domain `IntegrationProbe` port with throwaway connections. Blank stored values mean "unset" and fall back to environment.

## System Seeds

- System seed initialization, including menu enum seeds, must be controlled by configuration instead of hard-coded overwrite behavior.
- Menu seed sync mode is configured by `yudream.system.seed.menu.sync-mode`:
  - `INIT_EMPTY`: initialize only when the menu table/collection is empty;
  - `MISSING_ONLY`: insert only seed menu records whose code does not exist;
  - `OVERWRITE`: save every seed menu and overwrite records with the same code.
- Seed strategy judgment belongs in domain services. Infrastructure reads configuration and passes the strategy into the domain service during startup.

## Permission Codes

- Permission code shape: `{domain}:{resource}:{action}` (optionally `{domain}:{subdomain}:{resource}:{action}`); plugins use `plugin:{pluginCode}:{action}`; `@PermissionRegister.module` is a Chinese module name.
- The trailing action word drives the role permission tree's business grouping and color; pick from the vocabulary in root `AGENTS.md` section "权限码业务分类" (view: `view`/`export`/`download`; operate: `create`/`edit`/`import`/`upload`/`use`/`generate`/`publish`/`connect`/`send`/`test`/`run`/`invoke`/`execute`/`enable`/`disable`/`assign-*`/`dataset`/`report`/`accept`; manage: `manage`/`config`; danger: `delete`/`kickout`/`revoke`/`impersonate`). Unknown actions fall into the "其他" group. Adding a new action word requires updating AGENTS.md, the shared `src/utils/permission-catalog.ts` `permissionCategoryMeta`, and `knowledge.json` in the same commit.
- Split permissions per resource into independent `view`/write/`delete` codes; do not register a single `manage` and re-check finer levels in code.
- 权限树、矩阵和溯源统一按「业务域 → 稳定资源码 → 动作分类 → 权限」展示，集中复用 `permission-catalog.ts`，`assign-*` 匹配规范非空后缀，不以中文模块名作资源 key。历史入口码与接口动作同资源展示但保留独立叶子，插件历史嵌套码归稳定 owner。历史非标准码列入其他并兼容保留，勾选只更新可见叶子，全选合并且保留未知插件码。权限分配树按叶子分页叠加虚拟窗口，目录与搜索索引缓存，勾选只更新本页可见叶并保留跨页选择，明确区分本页和全目录操作；矩阵默认只读、明确开启编辑后提交差量；角色权限/用户角色以原完整数组 CAS，冲突拒绝覆盖，系统超管与历史 `*` 角色锁定单项操作。权限全景用户/菜单读取须分别检查其 view 权限，全量成员/闲置统计在仓储聚合，菜单关系复用真实可见码计算。会话上下文只允许读取操作者自己的 tokenSession；他人使用明确标记的默认部门/角色上下文，禁止暗示其真实在线会话。目录按 ANNOTATION → 实际注册 MANUAL → MENU 补缺，元数据同码稳定归一，菜单不覆盖端点；CATEGORY/LAYOUT 不独立登记。重建只操作 sysPermission，先备份验证并核对角色/用户/菜单指纹，不播种授权或重启插件。细则与验证见 `docs/system/permission-management.md`。

## Excel / Import Export

- Use EasyExcel for `.xlsx` import/export.
- Put HTTP file writing/reading helpers in support classes such as `ExcelHttpSupport`.
- Put row mapping and template row creation in interface assemblers.
- Export endpoints return blob responses from the frontend; frontend utilities should use `saveExcelResponse`, `pickExcelFile`, `excelForm`, and `importResultMessage` from `src/utils/excel.ts`.
- Add matching permissions to menu seed enum when adding `v-auth` buttons.
- Host common file uploads (`/api/files/upload`) must keep `spring.servlet.multipart.max-file-size`/`max-request-size` aligned with nginx `client_max_body_size` (currently `10240MB`). Set Tomcat `max-http-form-post-size`/`max-swallow-size` to `-1` so Tomcat does not truncate the body a second time. `GlobalExceptionHandler` must map `MaxUploadSizeExceededException` and nested size-limit exceptions to HTTP 400 with the Chinese business message `上传文件超过大小限制`, never the 500 fallback. Large XHR uploads (host `files.upload` and plugin blob uploads) must use `timeout: 0`; the axios default 60s will abort Minecraft map ZIPs.

## UTF-8 / Chinese Text

- Do not convert Chinese strings to `\uXXXX` escapes.
- Do not change files merely because PowerShell prints mojibake.
- Do fix actual source text that contains mojibake, such as Chinese text decoded into garbled CJK characters or broken question-mark fragments.
- Prefer normal Chinese in source files when existing project files are already UTF-8.
- Keep generated frontend display text localized in Chinese.

## Review Checklist

Before finishing backend work, run targeted scans:

```powershell
rg -n "private .*to[A-Z]|new .*Cmd|new .*ExcelRow|\\.builder\\(\\)" yudream-interfaces/src/main/java/online/yudream/base/interfaces -g "*Controller.java"
rg -n "\\\\u[0-9a-fA-F]{4}|\\?\\)" yudream-domain/src/main/java yudream-application/src/main/java yudream-infrastructure/src/main/java yudream-interfaces/src/main/java yudream-bootstrap/src/main/java -g "*.java"
```

Then verify:

```powershell
$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'; $env:Path="$env:JAVA_HOME/bin;$env:Path"; mvn -pl yudream-bootstrap -am -DskipTests compile
```

For frontend changes:

```powershell
pnpm --config.engine-strict=false --filter @fantastic-admin/core-arco-design-vue exec vue-tsc --noEmit --pretty false --skipLibCheck --ignoreDeprecations 6.0
```

## Git Workflow

- After completing each independently usable module, run the relevant verification, stage only files for that module, and create a git commit before moving to the next module.
- Write commit messages in Chinese and make them describe the completed module or fix, for example `feat: 完成用户管理模块` or `fix: 修复菜单图标显示`.
- If a module changes or corrects project architecture rules, include the matching `.codex/skills/yudream-ddd-architecture` update in the same commit.
- Do not include unrelated local files such as IDE metadata or temporary setup files unless the user explicitly asks.
- If a module cannot be committed because verification fails or the working tree contains conflicting user changes, report the blocker before continuing.

## Incremental Knowledge

Use `references/knowledge.json` for project-specific lessons, utilities, and recurring corrections. Add entries when:
- the user repeatedly corrects a style or architecture issue;
- a bug has a reusable diagnosis/fix;
- a utility, endpoint, or component is useful enough to reuse;
- a rule is important but too detailed for the core `SKILL.md`.

Append with:

```powershell
python .codex/skills/yudream-ddd-architecture/scripts/add_knowledge.py rule controller-no-mapping "Controller must not create Cmd/Res/ExcelRow; put conversions in interface assembler."
```
