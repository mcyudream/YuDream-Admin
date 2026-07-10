# Project Progress Plugin Implementation Plan

> Historical note (post-split): this plan was written before official business plugin source was moved out of the core repository. Paths like `yudream-plugins/yudream-plugin-project-progress` and `yudream-frontend/packages/plugin-project-progress` should now be read as paths inside the standalone plugin repository `yudream-admin-plugins`, not inside the core repository.

> **For implementer:** Use TDD throughout where the module can support it. Write failing tests for domain/application rules before implementation. Watch them fail, implement, then verify.

**Goal:** Build a standalone `project-progress` plugin for project progress monitoring, task assignment, multi-method check-in, Minecraft online-duration check-in, acceptance, and email notifications.

**Architecture:** Add a stable mail SPI in `yudream-plugin-spi`, adapt it in host infrastructure, then add a DDD-shaped plugin module in the standalone plugin repository `yudream-admin-plugins` under `yudream-plugins/yudream-plugin-project-progress`. Add a frontend remote plugin package under `yudream-frontend/packages/plugin-project-progress` there and package its `dist` into the plugin jar.

**Tech Stack:** Java 21, Maven, YuDream plugin SPI, Mongo-backed `PluginDocumentStore`, `PluginFileStore`, existing mail domain service, Vue 3, Vite, TypeScript, `@yudream/plugin-sdk`.

---

## Task 1: Add Mail SPI

**Files:**

- Create: `yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/mail/PluginMailMessage.java`
- Create: `yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/mail/PluginMailService.java`
- Create: `yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/mail/package-info.java`
- Modify: `yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/FrameworkServices.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/plugin/service/PluginMailFrameworkService.java`
- Modify: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/plugin/service/DefaultFrameworkServices.java`

**Steps:**

1. Write a compile-facing SPI design with immutable records.
2. Add `PluginMailService#send(PluginMailMessage message)`.
3. Add `FrameworkServices#mail()`.
4. Implement the host adapter using existing `MailSender`.
5. Verify `mvn -pl yudream-infrastructure -am -DskipTests compile`.

## Task 2: Scaffold Backend Plugin Module

**Files:**

- Modify: `pom.xml`
- Create: `yudream-plugins/yudream-plugin-project-progress/pom.xml`
- Create: `yudream-plugins/yudream-plugin-project-progress/src/main/resources/META-INF/services/online.yudream.base.plugin.spi.core.YuDreamPlugin`
- Create: `yudream-plugins/yudream-plugin-project-progress/src/main/java/online/yudream/base/plugin/projectprogress/bootstrap/ProjectProgressPlugin.java`
- Create: `yudream-plugins/yudream-plugin-project-progress/src/main/java/online/yudream/base/plugin/projectprogress/infrastructure/support/JsonSupport.java`

**Steps:**

1. Add the Maven module after `minecraft-activity-proof`.
2. Depend only on `yudream-plugin-spi`, Jackson, and test dependencies.
3. Register plugin metadata, permissions, frontend routes, repository, app service, and HTTP controller.
4. Verify `mvn -pl yudream-plugins/yudream-plugin-project-progress -am -DskipTests compile`.

## Task 3: Implement Domain Model and Tests

**Files:**

- Create domain enums for assignment mode, check-in type, acceptance result, event type.
- Create value objects: status option, Minecraft policy, location evidence, Minecraft evidence.
- Create aggregates: project, work detail, check-in record, acceptance record, event.
- Create domain service: random assignment and access rule helpers.
- Create tests under `yudream-plugins/yudream-plugin-project-progress/src/test/java/...`.

**Steps:**

1. Write tests for default status validation, random assignment size, claim capacity, check-in interval, and acceptance transitions.
2. Implement minimal domain classes until tests pass.
3. Keep messages in normal Chinese.
4. Verify plugin tests and compile.

## Task 4: Implement Repository Contract and Document Repository

**Files:**

- Create: `domain/repo/ProjectProgressRepository.java`
- Create: `infrastructure/repository/ProjectProgressDocumentRepository.java`

**Steps:**

1. Define repository methods for projects, details, check-ins, acceptance records, and events.
2. Implement mapping to/from `PluginDocumentStore` maps.
3. Add repository mapping tests if practical with an in-memory fake document store.
4. Verify plugin tests.

## Task 5: Implement Application Layer

**Files:**

- Create commands, queries, DTOs, and app assembler.
- Create: `application/service/ProjectProgressAppService.java`
- Create: `application/service/ProjectProgressNotificationService.java`
- Create: `application/service/ProjectProgressMinecraftService.java`
- Create: `application/service/ProjectProgressEventStream.java`

**Steps:**

1. Write application tests with fake repository/framework services for assignment email, rejection email, optional Minecraft status, and interval rejection.
2. Implement project/detail/check-in/acceptance use cases.
3. Store events for state changes and push events to stream subscribers.
4. Treat mail failures as non-fatal event metadata.
5. Verify plugin tests.

## Task 6: Implement HTTP Boundary

**Files:**

- Create request/res classes.
- Create: `interfaces/assembler/ProjectProgressWebAssembler.java`
- Create: `interfaces/http/ProjectProgressHttpFacade.java`
- Create: `interfaces/controller/ProjectProgressController.java`

**Steps:**

1. Keep controller methods as thin endpoint declarations.
2. Put request-to-command and DTO-to-response mapping in interface assembler/facade.
3. Support JSON routes and SSE route.
4. Verify targeted controller scan does not find disallowed controller mapping patterns.

## Task 7: Implement Frontend Plugin Package

**Files:**

- Create: `yudream-frontend/packages/plugin-project-progress/package.json`
- Create: `tsconfig.json`, `vite.config.ts`
- Create: `src/index.ts`, `src/ProjectProgressPlugin.vue`, `src/types.ts`, `src/styles.css`
- Create: `src/api/project-progress-api.ts`
- Create: composable and pages/components.

**Steps:**

1. Mirror existing plugin package conventions.
2. Build separate routes/pages: dashboard, projects, my tasks, check-ins, acceptance, settings.
3. Use SDK HTTP client only.
4. Keep UI dense and operational rather than marketing-style.
5. Verify `pnpm --config.engine-strict=false --filter @yudream/plugin-project-progress run typecheck`.

## Task 8: Package Frontend Dist into Plugin Jar

**Files:**

- Modify: `yudream-plugins/yudream-plugin-project-progress/pom.xml`

**Steps:**

1. Add frontend `dist` resource mapping to `META-INF/yudream-plugin/frontend/project-progress`.
2. Run frontend build if dependencies are installed.
3. Run backend compile.

## Task 9: Final Verification

**Commands:**

```powershell
$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'; $env:Path="$env:JAVA_HOME/bin;$env:Path"; mvn -pl yudream-plugins/yudream-plugin-project-progress -am test
$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'; $env:Path="$env:JAVA_HOME/bin;$env:Path"; mvn -pl yudream-bootstrap -am -DskipTests compile
pnpm --config.engine-strict=false --filter @yudream/plugin-project-progress run typecheck
```

**Expected:**

- Plugin tests pass.
- Backend compiles through bootstrap.
- Frontend package typechecks, or any unrelated workspace blocker is documented.

## Commit Strategy

Commit in usable slices:

```text
feat: 增加插件邮件通知SPI
feat: 搭建项目进度插件后端核心
feat: 完成项目进度插件打卡验收能力
feat: 完成项目进度插件前端页面
```
