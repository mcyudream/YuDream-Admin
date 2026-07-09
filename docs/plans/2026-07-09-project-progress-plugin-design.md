# Project Progress Plugin Design

## Context

YuDream Admin already has a plugin runtime with SPI-only contracts for plugin jars. Business plugins live under `yudream-plugins/` and frontend plugin packages live under `yudream-frontend/packages/plugin-*`. The existing `minecraft-server` plugin exposes server and player activity through `PluginMinecraftService`, so a new project progress plugin can read Minecraft online duration without directly depending on the Minecraft plugin implementation.

Current reusable platform abilities:

- Plugin documents and files are available through `PluginDocumentStore` and `PluginFileStore`.
- Users and security context are exposed through `PluginUserService` and `PluginSecurityService`.
- Minecraft server/player activity is exposed through `PluginMinecraftService` when the `minecraft-server` plugin is enabled.
- Plugin frontend routes, menus, permissions, and static assets are registered through plugin annotations and packaged as remote frontend assets.

Missing foundation:

- Plugins cannot send normal business emails through SPI today. The first implementation must add a stable `PluginMailService` port in `yudream-plugin-spi` and implement it in the host infrastructure/application adapter.

## Goals

- Add a standalone `project-progress` plugin for project management, progress monitoring, task assignment, check-in, and acceptance.
- Let administrators create projects with custom progress statuses, member scope, minimum check-in interval, and allowed check-in methods.
- Let each project contain multiple work details. A work detail can require one or more assignees.
- Support two assignment modes when administrators publish work details:
  - user claim;
  - random assignment from project members.
- Notify assigned users by email.
- Let users submit check-ins by image, file, location, or Minecraft online duration.
- If `minecraft-server` is enabled, support automatic check-in when configured online duration reaches a threshold.
- Let authorized administrators accept/reject work details. Rejecting a detail sends a rework email to assignees.
- Provide real-time-ish progress monitoring through query endpoints and an SSE activity stream.
- Provide a frontend plugin with separate pages for dashboard, project management, my tasks, check-ins, acceptance queue, and settings.

## Non-Goals

- Do not integrate with the existing activity proof plugin in the first version. Activity proof can later consume project participants or accepted check-in results.
- Do not build advanced project planning features such as dependencies, Gantt charts, budgeting, time sheets, or resource capacity planning.
- Do not require `minecraft-server`; Minecraft check-in remains optional and disabled when the extension is absent.
- Do not depend on host `domain`, `application`, `infrastructure`, or `interfaces` modules from plugin code.
- Do not expose Java `Long` identifiers as JSON numbers. All plugin HTTP IDs are strings at the boundary.

## Recommended Architecture

Add backend module:

```text
yudream-plugins/yudream-plugin-project-progress
  src/main/java/online/yudream/base/plugin/projectprogress
    bootstrap
    domain
      aggregate
      enumerate
      repo
      service
      valobj
    application
      assembler
      cmd
      dto
      query
      service
    infrastructure
      repository
      service
      support
    interfaces
      assembler
      controller
      http
      request
      res
```

Add frontend package:

```text
yudream-frontend/packages/plugin-project-progress
  src
    api
    components
    composables
    pages
    ProjectProgressPlugin.vue
    index.ts
    styles.css
    types.ts
```

Add SPI mail port:

```text
yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/mail
  PluginMailMessage.java
  PluginMailService.java
```

Extend `FrameworkServices` with:

```java
PluginMailService mail();
```

Host adapter maps `PluginMailMessage` to the existing `MailMessage` aggregate and delegates to the host `MailSender`.

## Domain Model

### ProjectProgressProject

Owns project-level configuration:

- `id`
- `name`
- `description`
- `managerUserIds`
- `memberUserIds`
- `statuses`
- `defaultStatusCode`
- `doneStatusCode`
- `minCheckInIntervalMinutes`
- `allowedCheckInTypes`
- `minecraftPolicy`
- `enabled`
- timestamps

`statuses` are administrator-defined values such as `TODO`, `DONE`, `REPAIRING`, `REVIEWING`. The plugin stores a code, label, terminal flag, and sort order.

`minecraftPolicy` includes:

- enabled flag;
- server id;
- required online minutes;
- include AFK flag;
- auto-check-in enabled flag.

### ProjectWorkDetail

Owns one work detail under a project:

- `id`
- `projectId`
- `title`
- `description`
- `statusCode`
- `assignmentMode`
- `requiredAssigneeCount`
- `candidateUserIds`
- `assigneeUserIds`
- `claimedUserIds`
- `acceptorUserIds`
- `published`
- `dueAt`
- timestamps

Main operations:

- publish;
- claim;
- random assign;
- update status;
- mark pending acceptance;
- accept;
- reject for rework.

### ProjectCheckInRecord

Stores user check-in evidence:

- `id`
- `projectId`
- `detailId`
- `userId`
- `type`
- `summary`
- `fileObjectKeys`
- `imageObjectKeys`
- `location`
- `minecraftEvidence`
- `createdAt`

Check-in types:

- `IMAGE`
- `FILE`
- `LOCATION`
- `MINECRAFT_ONLINE`

### ProjectAcceptanceRecord

Stores review history:

- `id`
- `projectId`
- `detailId`
- `operatorUserId`
- `result`
- `fromStatusCode`
- `toStatusCode`
- `reason`
- `createdAt`

### ProjectProgressEvent

Stores activity feed entries for dashboard/SSE catch-up:

- project created/updated;
- work detail published/assigned/claimed;
- check-in submitted;
- Minecraft auto-check-in generated;
- accepted/rejected.

## Application Services

`ProjectProgressAppService` orchestrates:

- project CRUD and status configuration;
- work detail CRUD/publish/assignment/claim;
- check-in creation;
- Minecraft online duration checks;
- acceptance and rejection;
- event feed;
- email notification.

`ProjectProgressNotificationService` wraps mail message creation so application use cases remain readable.

`ProjectProgressMinecraftService` wraps optional `PluginMinecraftService` lookup. It returns dependency status when Minecraft is missing instead of making the whole plugin fail.

## HTTP API

All routes are mounted under `/api/plugins/project-progress`.

Project management:

```text
GET    /status
GET    /projects
POST   /projects
GET    /projects/{projectId}
PUT    /projects/{projectId}
DELETE /projects/{projectId}
```

Work details:

```text
GET    /projects/{projectId}/details
POST   /projects/{projectId}/details
PUT    /details/{detailId}
DELETE /details/{detailId}
POST   /details/{detailId}/publish
POST   /details/{detailId}/claim
POST   /details/{detailId}/random-assign
```

Check-in:

```text
GET    /details/{detailId}/check-ins
POST   /details/{detailId}/check-ins
POST   /details/{detailId}/check-ins/minecraft
POST   /projects/{projectId}/minecraft/auto-check-ins
```

Acceptance:

```text
GET    /acceptance/pending
POST   /details/{detailId}/accept
POST   /details/{detailId}/reject
GET    /details/{detailId}/acceptance-records
```

Realtime feed:

```text
GET    /projects/{projectId}/events
GET    /projects/{projectId}/events/stream
```

## Permissions

```text
plugin:project-progress:view
plugin:project-progress:manage
plugin:project-progress:assign
plugin:project-progress:check-in
plugin:project-progress:accept
```

Rules:

- View permission can list visible projects and details.
- Manage permission can create/update/delete projects and details.
- Assign permission can publish, random assign, and adjust assignees.
- Check-in permission can submit evidence for current user's assigned or claimable work.
- Accept permission can accept/reject details when current user is in `acceptorUserIds` or project manager list.

## Frontend Pages

Register a top-level menu `项目进度` with separate routes:

```text
/platform/plugins/project-progress/dashboard
/platform/plugins/project-progress/projects
/platform/plugins/project-progress/my-tasks
/platform/plugins/project-progress/check-ins
/platform/plugins/project-progress/acceptance
/platform/plugins/project-progress/settings
```

Page responsibilities:

- Dashboard: project cards, completion ratio, pending acceptance, overdue details, event feed.
- Projects: project list, project editor, status editor, member manager, Minecraft policy.
- My Tasks: assigned and claimable details, claim action, status and due date.
- Check-ins: submit image/file/location/Minecraft evidence and view history.
- Acceptance: pending details, evidence list, accept/reject form.
- Settings: plugin dependency status and notification defaults.

## Data Flow

### Random Assignment

```text
Admin publishes detail
  -> application loads project members and candidate users
  -> domain service selects required assignee count
  -> repository saves detail assignees
  -> notification service emails assignees
  -> event is stored and sent to SSE subscribers
```

### User Claim

```text
User opens claimable details
  -> claim request
  -> application verifies project membership and capacity
  -> detail records user as assignee
  -> assignment email/event generated
```

### Manual Check-In

```text
User submits check-in evidence
  -> interface assembler maps request to command
  -> application verifies assigned/claimable access and interval
  -> files/images are stored through PluginFileStore when present
  -> record is persisted
  -> detail progress event is stored and streamed
```

### Minecraft Auto Check-In

```text
Scheduler or admin-triggered endpoint
  -> application loads project Minecraft policy
  -> optional PluginMinecraftService returns player activity
  -> user is matched by configured user/player identity strategy
  -> online duration is compared to required minutes
  -> duplicate interval is checked
  -> MINECRAFT_ONLINE check-in record is created
```

Identity matching starts with practical rules:

- match Minecraft `playerName` to username;
- match `playerId` to username when exact;
- later extension can consume skin/student mapping plugins if needed.

### Acceptance

```text
Admin reviews detail evidence
  -> application checks accept permission and acceptor membership
  -> accept updates status to done status
  -> reject updates status to configured rework status or previous status
  -> acceptance record is saved
  -> rejection email is sent to assignees
  -> event is stored and streamed
```

## Error Handling

- Business validation throws `IllegalArgumentException` or project plugin domain exceptions with normal Chinese messages.
- Missing optional Minecraft plugin returns dependency status for UI and rejects only Minecraft-specific operations.
- Missing mail configuration should not block persistence. Notification failures are recorded in event metadata and surfaced to administrators.
- Check-in interval violations return a clear next-allowed time.
- Random assignment rejects when candidate count is lower than required assignee count.
- Acceptance rejects if the project has no configured done status.
- File upload check-ins validate non-empty content and allowed file/image fields.

## Testing Strategy

Backend:

- Domain tests for status validation, claim capacity, random assignment, check-in interval, and acceptance transitions.
- Application tests with fake repositories/framework services for assignment emails, rejection emails, and Minecraft optional dependency behavior.
- Repository mapping tests for document store conversions.
- Compile gate:

```powershell
$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'; $env:Path="$env:JAVA_HOME/bin;$env:Path"; mvn -pl yudream-plugins/yudream-plugin-project-progress -am -DskipTests compile
```

Frontend:

- Type check the plugin package:

```powershell
pnpm --config.engine-strict=false --filter @yudream/plugin-project-progress run typecheck
```

Manual checks:

- Create project with custom statuses.
- Publish detail in random assignment mode and verify assignee email.
- Claim a detail in claim mode.
- Submit image/file/location check-ins.
- Enable Minecraft policy and generate online-duration check-in when `minecraft-server` is enabled.
- Accept and reject a detail; verify rejection email.
- Observe dashboard/event stream update.

## Implementation Order

### Phase 1: SPI Mail Port

Deliver:

- `PluginMailService` and `PluginMailMessage` in SPI.
- `FrameworkServices.mail()`.
- Host adapter in infrastructure using existing `MailSender`.

### Phase 2: Backend Plugin Core

Deliver:

- Maven module and service loader.
- Domain aggregates/enums/value objects/repository contract.
- Document repository implementation.
- Application service and notification/minecraft adapters.
- HTTP controller/facade/assembler/request/res classes.
- Plugin annotations for permissions and frontend routes.

### Phase 3: Check-In Evidence and SSE

Deliver:

- File/image evidence persistence through `PluginFileStore`.
- Location evidence model.
- Minecraft manual and auto-check-in endpoints.
- Project event stream.

### Phase 4: Frontend Plugin

Deliver:

- Plugin package, API wrapper, types, composable state.
- Dashboard, projects, my tasks, check-ins, acceptance, and settings pages.
- Styling consistent with existing plugin packages.

### Phase 5: Verification and Packaging

Deliver:

- Backend compile.
- Frontend type check/build where dependencies allow.
- Maven resource packaging of frontend `dist`.

## Future Extension

After this plugin is stable, the activity proof plugin can consume accepted project participants or selected check-in records to generate project participation proofs. That integration should be added through a new SPI/query extension rather than direct dependency between plugin implementations.
