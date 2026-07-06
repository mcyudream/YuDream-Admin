# Platform Extensions Design

## Goal

Build system security plus a platform-level optional capability system for YuDream Admin covering:

- API encryption with runtime enable/disable.
- Optional dual-token support.
- Optional API key support similar to GitHub access tokens, with permissions inherited from or constrained by the creator.
- Passkey support.
- API documentation support.
- OAuth support as both authorization server and OAuth client.
- Configurable HTTP invocation ability.
- Runtime execution for other languages, with Python as the first-class runtime.
- Word template generation for reports, activity certificates, and similar template files.
- WordPress-like customization for public pages: highly customizable home page and Markdown-based single pages.
- Neo4j graph database support.

This uses the following separation:

- `system` manages the admin system itself, including security, authentication, API keys, Passkey, OAuth, and API encryption.
- `platform` manages optional project infrastructure and extensible runtime capabilities. Capabilities with external dependencies, such as MQ, Neo4j, Python runtime, and document rendering, must be dynamically enabled/disabled and must not be required by projects that do not need them.
- Local middleware is grouped in `docker-compose.platform.yml` with Docker Compose profiles. RabbitMQ uses profile `mq`; Neo4j uses profile `graph`. Do not start all platform middleware by default for projects that do not need it.

## Current Foundation

Already implemented:

- `platform/capability` DDD module.
- Dynamic platform capability registry with enable, disable, config, health, and test actions.
- SSE provider.
- WebSocket provider.
- RabbitMQ provider and Docker Compose support.
- Platform frontend page at `/platform/capability`.

Local Docker examples:

```powershell
docker compose -f docker-compose.platform.yml --profile mq up -d rabbitmq
docker compose -f docker-compose.platform.yml --profile graph up -d neo4j
docker compose -f docker-compose.platform.yml --profile mq --profile graph up -d
```

The new extension modules should reuse this capability model when a feature has runtime status or external dependencies. External middleware must be optional and disabled by default unless the project explicitly enables it.

## Recommended Architecture

Use `system/security` for security and one top-level `platform` bounded context per optional capability family:

```text
domain/system/security
application/system/security
infrastructure/system/security
interfaces/system/security

domain/platform/docs
application/platform/docs
infrastructure/platform/docs
interfaces/platform/docs

domain/platform/integration
application/platform/integration
infrastructure/platform/integration
interfaces/platform/integration

domain/platform/document
application/platform/document
infrastructure/platform/document
interfaces/platform/document

domain/platform/cms
application/platform/cms
infrastructure/platform/cms
interfaces/platform/cms

domain/platform/graph
application/platform/graph
infrastructure/platform/graph
interfaces/platform/graph
```

Frontend routes:

```text
views/system/security
views/platform/api-doc
views/platform/integration
views/platform/document
views/platform/cms
views/platform/graph
```

Menu groups:

```text
系统管理
  安全中心

平台能力
  能力管理
  API 文档
  集成调用
  文档模板
  内容定制
  图数据库
```

Permission prefixes:

```text
system:security:*
platform:docs:*
platform:integration:*
platform:document:*
platform:cms:*
platform:graph:*
```

## Module Responsibilities

### 1. System Security

Scope:

- API encryption switch and algorithm config.
- Dual-token support switch.
- Access token and refresh token policy config.
- API key lifecycle.
- API key permission assignment.
- API key creator permission ceiling.
- Passkey registration and login support.
- OAuth authorization server support.
- OAuth client support.

Suggested domain model:

```text
aggregate/ApiSecurityPolicy
aggregate/ApiKeyCredential
aggregate/OAuthClientRegistration
aggregate/OAuthProviderRegistration
aggregate/PasskeyCredential
valobj/ApiKeySecret
valobj/PermissionScope
valobj/TokenPolicy
enumerate/CredentialStatus
enumerate/OAuthGrantType
repo/ApiSecurityPolicyRepo
repo/ApiKeyCredentialRepo
repo/OAuthClientRegistrationRepo
repo/OAuthProviderRegistrationRepo
repo/PasskeyCredentialRepo
service/ApiKeyPermissionPolicy
service/TokenPolicyService
```

Important rules:

- API keys must never store raw secrets. Store only hash, prefix, masked value, last used time, expiry, and permission scope.
- API key permissions cannot exceed the creator's effective permissions at creation time unless a super admin explicitly grants them.
- API encryption must be feature-flagged so existing frontend/backend traffic still works when disabled.
- Dual-token mode must be feature-flagged so current Sa-Token login can continue unchanged until migration is complete.
- Passkey should be additive first, not a replacement for password login.
- OAuth server and client settings must be separate. The project may issue tokens to third-party clients and also log in through third-party providers.

### 2. API Docs

Scope:

- API documentation UI entry.
- Permission-protected access.
- Optional API key authentication for docs access.
- Environment-aware docs endpoint config.

Implementation direction:

- Prefer SpringDoc OpenAPI for Java API discovery.
- Register the docs page under `platform:docs`.
- Expose docs only when enabled in system security/docs settings.

### 3. Platform Integration

Scope:

- Configurable HTTP invocation.
- Request templates.
- Headers, body, query params, timeout, retry.
- Response mapping.
- Script execution, Python first.
- Runtime sandbox config and audit logs.

Suggested model:

```text
aggregate/HttpConnector
aggregate/HttpInvocationLog
aggregate/RuntimeScript
aggregate/RuntimeExecutionLog
service/HttpInvocationGateway
service/RuntimeExecutor
```

Rules:

- HTTP invocation must redact secrets in logs.
- Script execution must have timeout, working directory isolation, stdout/stderr capture, and disabled-by-default dangerous environment access.
- Python is the first runtime. Other runtimes can be added as providers later.

### 4. Document Templates

Scope:

- Word template upload and management.
- Placeholder metadata.
- Report/activity certificate generation.
- Generated file storage through existing S3/RustFS file module.

Suggested implementation:

- Use DOCX template rendering, likely poi-tl or docx4j depending on template requirements.
- Store templates as file objects plus template metadata.
- Provide preview data and generation history.

### 5. CMS Customization

Scope:

- Public home page customization.
- Highly customizable home page sections.
- Single-page content management.
- Markdown editor for simple pages.
- Publish/draft workflow.

Suggested model:

```text
aggregate/CmsPage
aggregate/HomePageLayout
aggregate/CmsAsset
valobj/PageSlug
valobj/PageContent
enumerate/PageStatus
```

Rules:

- Admin editing and public rendering must be separate.
- Markdown single pages should support draft, preview, publish, and SEO fields.
- Home page customization should use structured sections rather than arbitrary raw HTML in the first version.

### 6. Neo4j Graph

Scope:

- Neo4j connection capability.
- Graph schema/config management.
- Query execution with permission controls.
- Basic node/relationship browser.

Suggested implementation:

- Add Neo4j as `platform:graph` capability provider with health check.
- Use Docker Compose for Neo4j in local development, but do not start it by default. Projects that do not need graph storage should keep this capability disabled and avoid creating Neo4j containers.
- Keep graph data access behind application services.
- Restrict raw Cypher execution to explicit permission.

## Data Flow

### API Key Request

```text
HTTP Request
  -> Security Filter
  -> Extract API key
  -> Hash lookup
  -> Check status/expiry
  -> Load permission scope
  -> Bind request security context
  -> Existing permission checks
  -> Controller
```

### API Encryption Request

```text
Frontend encrypts payload
  -> HTTP request with encryption headers
  -> Encryption filter decrypts body
  -> Controller receives normal request model
  -> Response advice encrypts response when enabled
```

### Python Runtime Execution

```text
Admin creates script
  -> Application validates script policy
  -> Infra runtime executor starts isolated process
  -> Capture stdout/stderr/status/time
  -> Store execution log
  -> Return result DTO
```

### Word Template Generation

```text
Template metadata + input data
  -> Application validates placeholders
  -> Infra renderer creates DOCX
  -> Existing file service stores generated file
  -> Return file URL and generation log
```

## Error Handling

- Business validation uses `BizException` with normal Chinese messages.
- Infrastructure failures are wrapped into concise business-facing messages at application boundary.
- Secret values are never returned after creation except one-time API key plaintext.
- Runtime execution errors must include stdout/stderr summaries but redact configured secrets.
- OAuth and API key authentication failures must not reveal whether a key/client exists.

## Testing Strategy

Backend:

- Domain tests for permission ceiling, token policy, API key expiry, CMS publish rules, and template placeholder validation.
- Application tests for create/update/enable/disable flows.
- Infrastructure integration tests where reasonable; Docker-backed tests can be optional.
- Compile gate:

```powershell
$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'; $env:Path="$env:JAVA_HOME/bin;$env:Path"; mvn -pl yudream-bootstrap -am -DskipTests compile
```

Frontend:

- Type check:

```powershell
pnpm --config.engine-strict=false --filter @fantastic-admin/core-arco-design-vue exec vue-tsc --noEmit --pretty false --skipLibCheck --ignoreDeprecations 6.0
```

Manual checks:

- Security toggles do not break normal login when disabled.
- API key can access only allowed permissions.
- Passkey enrollment and login are additive.
- OAuth server/client configs are separate and visible.
- HTTP invocation logs redact secrets.
- Python runtime times out and captures output.
- Word template generates a downloadable file.
- CMS draft/preview/publish flow works.
- Neo4j health check reflects Docker container status.

## Implementation Order

### Phase 1: System Security Foundation

Deliver:

- System security settings aggregate.
- API encryption config model and disabled-by-default switch.
- Dual-token config model and disabled-by-default switch.
- API key aggregate, one-time secret creation, hashed storage, permission scope, expiry, revoke.
- Backend management APIs and frontend security center page.
- Menu and permissions under `system:security:*`.

Commit target:

```text
feat: 完成系统安全基础模块
```

### Phase 2: API Key Runtime Authentication

Deliver:

- API key request filter.
- Permission context bridge.
- Last-used audit update.
- Tests proving creator permission ceiling.

Commit target:

```text
feat: 接入API Key运行时认证
```

### Phase 3: API Docs

Deliver:

- SpringDoc dependency/config.
- Docs page and route.
- Docs enable/disable setting.

Commit target:

```text
feat: 完成平台API文档模块
```

### Phase 4: OAuth and Passkey

Deliver:

- Passkey registration/login models.
- OAuth server client registration.
- OAuth login provider registration.
- Admin pages.

Commit target:

```text
feat: 完成OAuth与Passkey基础能力
```

### Phase 5: HTTP Invocation and Python Runtime

Deliver:

- HTTP connector management.
- Invocation logs.
- Python script runtime executor.
- Runtime capability provider and Docker notes if needed.

Commit target:

```text
feat: 完成平台集成调用模块
```

### Phase 6: Word Template

Deliver:

- Template upload and placeholder metadata.
- Render endpoint.
- Generated file history.

Commit target:

```text
feat: 完成Word模板生成模块
```

### Phase 7: CMS Customization

Deliver:

- Page management.
- Markdown single pages.
- Home page structured layout.
- Public render endpoints.

Commit target:

```text
feat: 完成内容定制模块
```

### Phase 8: Neo4j

Deliver:

- Neo4j Docker Compose support.
- Neo4j capability provider.
- Connection config and health check.
- Basic graph query/browser APIs.

Commit target:

```text
feat: 完成Neo4j图数据库模块
```

## Open Decisions

- Encryption algorithm: start with AES-GCM plus RSA/ECDH key exchange, or simpler shared-key AES-GCM for first internal version.
- OAuth provider stack: Spring Authorization Server for server mode; Spring Security OAuth2 Client for client mode.
- Passkey library: evaluate WebAuthn4J vs Yubico java-webauthn-server.
- Word rendering library: evaluate poi-tl vs docx4j after template complexity is known.
- Python runtime isolation: local process first, Docker sandbox later, or Docker sandbox from the start.
- CMS public rendering: backend-rendered public pages or frontend-rendered public pages.

Default recommendation:

- Build Phase 1 first with API key and system security settings.
- Keep encryption and dual-token as configurable policy models in Phase 1, but do not force traffic migration until Phase 2+.
- Use the existing platform capability registry for dependencies that need runtime health checks. MQ, Neo4j, Python runtime, and similar middleware-backed features must remain optional and disabled until explicitly enabled.
