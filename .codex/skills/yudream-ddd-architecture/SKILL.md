---
name: yudream-ddd-architecture
description: Enforce YuDream Admin backend DDD layering and project-specific architecture. Use when modifying Java backend code, adding system modules, reviewing/refactoring controllers, assemblers, application services, domain aggregates, repositories, infrastructure implementations, Excel/import-export code, permission/menu code, or when the user asks about DDD conventions, Controller responsibilities, UTF-8/Chinese string cleanup, or project architecture rules.
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
- External systems (S3, Redis, Sa-Token gateway details, mail) live here behind domain/application contracts.

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

## Excel / Import Export

- Use EasyExcel for `.xlsx` import/export.
- Put HTTP file writing/reading helpers in support classes such as `ExcelHttpSupport`.
- Put row mapping and template row creation in interface assemblers.
- Export endpoints return blob responses from the frontend; frontend utilities should use `saveExcelResponse`, `pickExcelFile`, `excelForm`, and `importResultMessage` from `src/utils/excel.ts`.
- Add matching permissions to menu seed enum when adding `v-auth` buttons.

## UTF-8 / Chinese Text

- Do not convert Chinese strings to `\uXXXX` escapes.
- Do not change files merely because PowerShell prints mojibake.
- Do fix actual source text that contains mojibake such as `鐢ㄦ埛`, `绯荤粺`, `鍒犻櫎`, `鏄?`, `涓嶅瓨鍦?`.
- Prefer normal Chinese in source files when existing project files are already UTF-8.
- Keep generated frontend display text localized in Chinese.

## Review Checklist

Before finishing backend work, run targeted scans:

```powershell
rg -n "private .*to[A-Z]|new .*Cmd|new .*ExcelRow|\\.builder\\(\\)" yudream-interfaces/src/main/java/online/yudream/base/interfaces -g "*Controller.java"
rg -n "鐢ㄦ埛|绯荤粺|鍒犻櫎|鏄\\?|涓嶅瓨鍦|\\\\u[0-9a-fA-F]{4}" yudream-*/src/main/java -g "*.java"
```

Then verify:

```powershell
$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'; $env:Path="$env:JAVA_HOME/bin;$env:Path"; mvn -pl yudream-bootstrap -am -DskipTests compile
```

For frontend changes:

```powershell
pnpm --config.engine-strict=false --filter @fantastic-admin/core-arco-design-vue exec vue-tsc --noEmit --pretty false --skipLibCheck --ignoreDeprecations 6.0
```

## Incremental Knowledge

Use `references/knowledge.json` for project-specific lessons, utilities, and recurring corrections. Add entries when:
- the user repeatedly corrects a style or architecture issue;
- a bug has a reusable diagnosis/fix;
- a utility, endpoint, or component is useful enough to reuse;
- a rule is important but too detailed for the core SKILL.md.

Append with:

```powershell
python .codex/skills/yudream-ddd-architecture/scripts/add_knowledge.py rule controller-no-mapping "Controller must not create Cmd/Res/ExcelRow; put conversions in interface assembler."
```
