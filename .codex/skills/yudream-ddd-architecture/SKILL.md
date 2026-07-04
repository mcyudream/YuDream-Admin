---
name: yudream-ddd-architecture
description: Enforce YuDream Admin backend DDD layering and project-specific architecture. Use when modifying Java backend code, adding system modules, reviewing/refactoring controllers, assemblers, application services, domain aggregates, repositories, infrastructure implementations, Excel/import-export code, permission/menu code, module-level git commits, Chinese commit messages, or when the user asks about DDD conventions, Controller responsibilities, UTF-8/Chinese string cleanup, or project architecture rules.
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
- Optional engineering abilities such as SSE, WebSocket, MQ, Neo4j, Python Runtime, HTTP integration, document generation, CMS, and AI/Agent tooling are dynamically loadable platform abilities and belong in `platform`, separate from `system`.
- Platform capability runtime has two gates:
  - project gate: configuration file or Spring conditional annotation decides whether the project allows loading the capability provider, for example `yudream.platform.capabilities.rabbitmq.enabled=true` with `@ConditionalOnProperty`;
  - application gate: application service checks the persisted capability state before each use case and calls `ensureEnabled(...)` before invoking technical tools.
- A capability that is not allowed by the project gate must not appear as a runtime-toggleable provider, must not register endpoints/handlers, and must not be restored at application startup.
- A capability disabled by the application gate must reject use cases in the application layer and must not create external connections, declare middleware resources, or require projects to configure unused middleware.
- Infra providers are only tool wrappers. Provider construction and `enable(config)` may store local config or mark state, but must not open MQ/Neo4j/Redis-like connections, verify remote connectivity, declare queues/topics, or start long-running resources.
- External connections/resources may be created only after both gates pass and a business operation, connect action, health action, or explicit test action actually needs the external system. Close and clear resources on disable.
- Platform capabilities must declare runtime dependencies in `CapabilityDescriptor.dependencies`. Application services must reject enabling a capability when any dependency is unavailable or disabled, and disabling a dependency must disable dependent capabilities so the UI and runtime state remain truthful.
- CMS platform work must be a complete publishing loop, not only backend CRUD. A CMS change should include backend menu permissions, admin route/menu registration, public frontend routes, public rendering, publish/unpublish flow, SEO fields, page/template metadata, and visual-builder-compatible content storage such as saved HTML plus Markdown fallback.
- CMS visual editing uses GrapesJS on the frontend. Store the published HTML in `htmlContent`, the published CSS in `cssContent`, and the editable GrapesJS project source in `builderProjectJson`; homepage builder data lives in `HomePageLayout.settings.homeHtml`, `homeCss`, and `homeProjectJson` unless a dedicated backend aggregate field is later added. Keep Markdown/HTML editing and legacy homepage sections as fallback modes, but do not keep a second visual-builder dependency or old visual-builder component tree after migrating to GrapesJS.

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
