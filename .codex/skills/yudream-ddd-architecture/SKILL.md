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
- Spring AI integrations must use native tool calling (`ChatClient.toolCallbacks(...)`, `FunctionToolCallback`, or `@Tool`) for model-tool interaction. Do not ask the model to manually emit project-specific `toolCalls` JSON as the primary protocol. Keep YuDream `AiAgentTool` as the project/domain abstraction and adapt it to Spring AI in infrastructure; legacy JSON parsing is allowed only as a compatibility fallback.
- AI/Agent streaming endpoints must be streaming end to end: the HTTP SSE controller, application callback, and infrastructure model call should all propagate natural-language deltas through Spring AI `stream()` instead of wrapping a blocking `call()` as fake streaming. Match model read timeouts to the SSE timeout for long-running tool generation.
- Spring AI tool-calling streams may not emit natural-language deltas while the provider is planning tool arguments. For user-facing builders, expose a domain/application progress callback, send `ai.progress` milestones such as `analysis`, `subscribed`, `tool-start`, `tool-complete`, and send tool results immediately when callbacks execute instead of waiting for the final model summary.
- AI builder frontends using TDesign Chat should render deep-thinking/progress content with the built-in `thinking` content type (`t-chat-thinking`) when deep thinking is enabled; keep ordinary `ai.message` deltas as markdown/text and keep structured `ai.tool` events separate from chat text.
- Only real model reasoning, for example OpenAI-compatible `reasoningContent` / `reasoning_content`, should be rendered as TDesign `thinking`; lifecycle progress and heartbeat events must remain separate and must not be merged into the thinking panel.
- CMS builder should enable deep-thinking behavior at the backend/application boundary by default; do not expose a separate deep-thinking toggle in the builder sender UI unless product requirements explicitly reintroduce it.
- CMS AI builder chat history should be scoped by editing target, for example `home:home` or `page:{id}`, and loaded lazily on the frontend. Store only lightweight summaries in lists, load full messages on demand, and avoid persisting large base64 attachments in history records.
- General agent tools such as `web.fetch` are allowed when they are declared as `AiAgentTool`, have permission metadata, and are used for analysis before mutation tools. Mutation tools such as `cms.canvas.patch` should remain the only tools that directly change frontend state.
- OpenAI-compatible gateways may return JSON with non-standard response content types such as `application/octet-stream`. When using Spring AI `OpenAiApi` through `RestClient`, configure the Jackson message converter in infrastructure to also support those compatible JSON media types before assuming the model response is invalid.
- AI/Agent SSE events must use an extensible envelope instead of naked payloads. Use semantic event names such as `ai.message`, `ai.tool`, `ai.result`, and `ai.error`; the data body must include `event`, `action`, `module`, `traceId`, `timestamp`, and `payload`. Put text in `payload.content`, tool results in `payload.tool`, final results in `payload.result`, and errors in `payload.message`. Do not emit raw model JSON as chat content.
- Long-running AI/Agent SSE endpoints should send an immediate `ai.progress` event after accepting the request, before calling the model. Also send progress before expensive tool application so the frontend never appears idle while the model is planning tool arguments or the tool is mutating the canvas.
- After an SSE endpoint has sent a structured `ai.error` event, complete the emitter normally instead of calling `completeWithError`; otherwise Spring MVC may try to route the exception through global JSON handlers while the response content type is already `text/event-stream`.
- CMS platform work must be a complete publishing loop, not only backend CRUD. A CMS change should include backend menu permissions, admin route/menu registration, public frontend routes, public rendering, publish/unpublish flow, SEO fields, page/template metadata, and visual-builder-compatible content storage such as saved HTML plus Markdown fallback.
- CMS visual editing uses GrapesJS on the frontend. Store the published HTML in `htmlContent`, the published CSS in `cssContent`, and the editable GrapesJS project source in `builderProjectJson`; homepage builder data lives in `HomePageLayout.settings.homeHtml`, `homeCss`, and `homeProjectJson` unless a dedicated backend aggregate field is later added. Keep Markdown/HTML editing and legacy homepage sections as fallback modes, but do not keep a second visual-builder dependency or old visual-builder component tree after migrating to GrapesJS.

## Plugin Architecture

- `yudream-plugin-spi` is the only compile-time contract module that third-party plugin JARs may depend on. Plugin code must not depend on `yudream-domain`, `yudream-application`, `yudream-infrastructure`, `yudream-interfaces`, or bootstrap implementation classes.
- Plugin source modules live under `yudream-plugins/`, for example `yudream-plugins/yudream-plugin-spi`, `yudream-plugins/yudream-sample-plugin`, and `yudream-plugins/yudream-plugin-{code}`. Maven `-pl` commands should use those paths. Runtime plugin JARs may still be placed directly in the `plugins/` directory.
- Prefer annotation-driven plugin registration for static plugin metadata, permissions, menus, frontend routes, HTTP endpoints, capabilities, migrations, and extension declarations. Imperative `PluginContext.registerXxx(...)` calls are allowed for dynamic runtime contributions, conditional registrations, and compatibility fallback only. The plugin runtime should scan annotations, validate duplicate codes/routes/permissions at load time, and adapt the discovered declarations to the same runtime registry used by manual registration.
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
- Plugin JAR entry classes implement `core.YuDreamPlugin`. All menu, permission, capability, frontend, HTTP, and extension registrations must happen through `core.PluginContext` so hot unload can remove every runtime contribution.
- Plugins may call YuDream framework abilities only through SPI ports such as `system.FrameworkServices`, `system.user.PluginUserService`, and `system.security.PluginSecurityService`. If a new host ability is needed, add a stable SPI port/DTO first, then implement the adapter in the host application/runtime layer.
- Do not expose domain aggregate roots, domain repositories, application services, infrastructure repositories, MyBatis/Mongo data objects, or Spring beans directly to plugin code. Domain invariants still belong in the host domain/application layers; plugins receive DTO-style views and invoke use-case-oriented ports.
- Plugin-facing DTOs must be stable, serialization-friendly records or simple value objects. They should not reuse interface `request/res` classes or persistence `dataobj` classes.
- Java `Long` identifiers, especially Snowflake IDs, must cross JSON/plugin/front-end boundaries as strings. Plugin DTOs/responses/requests, TypeScript models, form state, URL params, and SDK calls must not expose those IDs as `number` or call `Number(id)`, because JavaScript loses precision above `Number.MAX_SAFE_INTEGER` and follow-up saves/selects will query the wrong record. Keep domain/application storage as `Long` when appropriate, but convert to `String` at the API boundary and parse back only inside backend application services.
- Plugin HTTP endpoints should be mounted under plugin-scoped paths such as `/api/plugins/{pluginCode}/**` through a runtime dispatcher for the first version. Avoid dynamic Spring MVC controller registration until unload semantics, permissions, and diagnostics are proven stable.
- Frontend plugins should use the host-provided dynamic frontend SDK/client instead of bundling a private axios instance. SDK version and remote entry metadata must be part of the frontend manifest so the host can refresh or replace SDK behavior without rebuilding every plugin.
- Frontend plugin pages must not be implemented as host `apps/*/src/views` business pages except for temporary compatibility shims. Embedding plugin business pages in the host frontend is treated as the same coupling as not using plugins. Use a workspace package under `yudream-frontend/packages/plugin-*` during local development, sharing `@fantastic-admin/components` and `@yudream/plugin-sdk`; production plugins must expose an ESM `remoteEntry.js` from the plugin JAR. For the standard JAR layout, omit `@PluginFrontend.entry` and let the runtime default it to `/api/platform/plugins/{pluginCode}/assets/remoteEntry.js`. Host runtime pages load the remote module and inject SDK/route props.
- Plugin frontend assets must be packaged under `META-INF/yudream-plugin/frontend/{pluginCode}` inside the plugin JAR, usually by copying `yudream-frontend/packages/plugin-{code}/dist` from the plugin module `pom.xml`. The runtime maps browser requests `/api/platform/plugins/{pluginCode}/assets/{assetPath}` to that classpath directory. Only set `entry` explicitly for non-standard external hosting or a non-standard entry file.
- Workspace package loading is only a local development convenience. Do not write production manifests that import workspace aliases such as `@yudream/plugin-blessing-skin`, and do not require the host app to depend on a specific plugin frontend package. The production contract is the remote ESM entry plus SDK.
- Frontend plugin packages must be engineered by responsibility instead of placing a whole plugin in one `.vue` file. Use at least `src/pages` for route pages, `src/components` for reusable UI, `src/composables` for stateful workflows, `src/api` for SDK-backed API wrappers, and `src/types.ts` for plugin view models. A top-level plugin shell may only select the current page and provide shared state; large forms, lists, dashboards, migration tools, repeated panels, and route-specific layouts must be split into pages/components.
- A frontend plugin route should map to a real page component, not to a giant tabbed component that hides all features inside one route. When a plugin has multiple management surfaces, define separate route entries and keep each page small enough that page logic, reusable panels, API calls, and types are independently testable.
- Plugin frontend manifests should support module-level top menu metadata, such as menu title, icon, and sort. A business plugin with multiple management surfaces should register multiple frontend routes under its own top-level menu instead of hiding major pages behind tabs in one runtime page. For example, a skin plugin should expose separate routes such as dashboard, player management, texture library, closet management, and system management.
- Runtime implementations must track disposables, classloaders, registered handlers, frontend manifests, menus, permissions, and capability providers by plugin code, and must release them on disable/unload.

## System Seeds

- System seed initialization, including menu enum seeds, must be controlled by configuration instead of hard-coded overwrite behavior.
- Menu seed sync mode is configured by `yudream.system.seed.menu.sync-mode`:
  - `INIT_EMPTY`: initialize only when the menu table/collection is empty;
  - `MISSING_ONLY`: insert only seed menu records whose code does not exist;
  - `OVERWRITE`: save every seed menu and overwrite records with the same code.
- Seed strategy judgment belongs in domain services. Infrastructure reads configuration and passes the strategy into the domain service during startup.

## Excel / Import Export

- Use EasyExcel for `.xlsx` import/export.
- Put HTTP file writing/reading helpers in support classes such as `ExcelHttpSupport`.
- Put row mapping and template row creation in interface assemblers.
- Export endpoints return blob responses from the frontend; frontend utilities should use `saveExcelResponse`, `pickExcelFile`, `excelForm`, and `importResultMessage` from `src/utils/excel.ts`.
- Add matching permissions to menu seed enum when adding `v-auth` buttons.

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
