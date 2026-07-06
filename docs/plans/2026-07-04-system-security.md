# System Security Implementation Plan

> **For implementer:** Use TDD throughout. Write failing test first. Watch it fail. Then implement.

**Goal:** Build the first system security module with configurable API encryption, dual-token switch, API key management, and extension points for Passkey and OAuth.

**Architecture:** Add a new `system/security` bounded context across domain, application, infrastructure, and interfaces. Keep runtime authentication separate from management CRUD so API key filters can be enabled after the management model is verified. Store only hashed API key secrets and enforce creator permission ceilings.

**Tech Stack:** Spring Boot 3.5, MongoDB, Sa-Token permission context, BCrypt or SHA-256/HMAC hashing, Vue 3 Fantastic-admin frontend.

---

## Task 1: Domain Security Policy and API Key Model

**Files:**
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/system/security/aggregate/ApiSecurityPolicy.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/system/security/aggregate/ApiKeyCredential.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/system/security/valobj/ApiKeySecret.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/system/security/valobj/PermissionScope.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/system/security/valobj/TokenPolicy.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/system/security/enumerate/CredentialStatus.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/system/security/repo/ApiSecurityPolicyRepo.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/system/security/repo/ApiKeyCredentialRepo.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/system/security/service/ApiKeyPermissionPolicy.java`

**Steps:**
1. Add domain tests if the project test layout is available; otherwise add compile-verifiable domain methods and cover with application service tests in later tasks.
2. Model policy switches:
   - `apiEncryptionEnabled`
   - `dualTokenEnabled`
   - `apiKeyEnabled`
   - `passkeyEnabled`
   - `oauthServerEnabled`
   - `oauthClientEnabled`
3. Model token policy:
   - access token ttl seconds
   - refresh token ttl seconds
   - refresh rotation enabled
4. Model API key credential:
   - name
   - key prefix
   - secret hash
   - creator user id
   - permission scope
   - expiry time
   - status
   - last used time
5. Add domain behavior:
   - revoke
   - mark used
   - expire check
   - permission ceiling validation
6. Verify compile:

```powershell
$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'; $env:Path="$env:JAVA_HOME/bin;$env:Path"; mvn -pl yudream-domain -am -DskipTests compile
```

7. Commit:

```powershell
git add yudream-domain/src/main/java/online/yudream/base/domain/system/security
git commit -m "feat: 完成系统安全领域模型"
```

## Task 2: Infrastructure Persistence

**Files:**
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/system/security/dataobj/ApiSecurityPolicyDO.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/system/security/dataobj/ApiKeyCredentialDO.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/system/security/mapper/ApiSecurityInfraMapper.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/system/security/mapper/ApiKeyCredentialInfraMapper.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/system/security/impl/ApiSecurityPolicyRepoImpl.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/system/security/impl/ApiKeyCredentialRepoImpl.java`

**Steps:**
1. Add Mongo DOs with unique index on API key prefix and policy code.
2. Keep raw key secret out of persistence.
3. Implement repo methods:
   - find default policy
   - save policy
   - find API key by prefix
   - page API keys
   - save API key
4. Verify compile:

```powershell
$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'; $env:Path="$env:JAVA_HOME/bin;$env:Path"; mvn -pl yudream-infrastructure -am -DskipTests compile
```

5. Commit:

```powershell
git add yudream-infrastructure/src/main/java/online/yudream/base/infra/system/security
git commit -m "feat: 完成系统安全持久化"
```

## Task 3: Application Services

**Files:**
- Create: `yudream-application/src/main/java/online/yudream/base/application/system/security/cmd/ApiSecurityPolicyUpdateCmd.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/system/security/cmd/ApiKeyCreateCmd.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/system/security/cmd/ApiKeyRevokeCmd.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/system/security/query/ApiKeyPageQuery.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/system/security/dto/ApiSecurityPolicyDTO.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/system/security/dto/ApiKeyCredentialDTO.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/system/security/dto/ApiKeyCreateResultDTO.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/system/security/assembler/ApiSecurityAssembler.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/system/security/service/ApiSecurityAppService.java`

**Steps:**
1. Generate one-time API key plaintext in application service.
2. Hash before saving.
3. Return plaintext only from create result.
4. Validate requested permissions against creator permissions.
5. Provide default policy creation when no policy exists.
6. Verify compile:

```powershell
$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'; $env:Path="$env:JAVA_HOME/bin;$env:Path"; mvn -pl yudream-application -am -DskipTests compile
```

7. Commit:

```powershell
git add yudream-application/src/main/java/online/yudream/base/application/system/security
git commit -m "feat: 完成系统安全应用服务"
```

## Task 4: Interface APIs and Menu

**Files:**
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/system/security/controller/ApiSecurityController.java`
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/system/security/assembler/ApiSecurityWebAssembler.java`
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/system/security/request/ApiSecurityPolicyUpdateRequest.java`
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/system/security/request/ApiKeyCreateRequest.java`
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/system/security/res/ApiSecurityPolicyRes.java`
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/system/security/res/ApiKeyCredentialRes.java`
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/system/security/res/ApiKeyCreateResultRes.java`
- Modify: `yudream-infrastructure/src/main/java/online/yudream/base/infra/system/menu/enumerate/SystemMenuModule.java`

**Steps:**
1. Add endpoints:
   - `GET /api/system/security/policy`
   - `PUT /api/system/security/policy`
   - `GET /api/system/security/api-keys`
   - `POST /api/system/security/api-keys`
   - `POST /api/system/security/api-keys/{id}/revoke`
2. Add permissions:
   - `system:security:view`
   - `system:security:edit`
   - `system:security:api-key:create`
   - `system:security:api-key:revoke`
3. Ensure Controller contains no request-to-command mapping logic; use interface assembler.
4. Run controller scan:

```powershell
rg -n "private .*to[A-Z]|new .*Cmd|new .*ExcelRow|\\.builder\\(\\)" yudream-interfaces/src/main/java/online/yudream/base/interfaces -g "*Controller.java"
```

5. Verify compile:

```powershell
$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'; $env:Path="$env:JAVA_HOME/bin;$env:Path"; mvn -pl yudream-bootstrap -am -DskipTests compile
```

6. Commit:

```powershell
git add yudream-interfaces/src/main/java/online/yudream/base/interfaces/system/security yudream-infrastructure/src/main/java/online/yudream/base/infra/system/menu/enumerate/SystemMenuModule.java
git commit -m "feat: 暴露系统安全管理接口"
```

## Task 5: Frontend Security Center

**Files:**
- Create: `yudream-frontend/apps/core-arco-design-vue/src/api/modules/system-security.ts`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/views/system/security/index.vue`

**Steps:**
1. Build a Fantastic-admin page with:
   - policy switches
   - token TTL inputs
   - API key list
   - API key create modal
   - one-time key secret reveal modal
   - revoke action
2. Use existing Fa components first.
3. Keep Chinese UI labels.
4. Verify type check:

```powershell
pnpm --config.engine-strict=false --filter @fantastic-admin/core-arco-design-vue exec vue-tsc --noEmit --pretty false --skipLibCheck --ignoreDeprecations 6.0
```

5. Commit:

```powershell
git add yudream-frontend/apps/core-arco-design-vue/src/api/modules/system-security.ts yudream-frontend/apps/core-arco-design-vue/src/views/system/security/index.vue
git commit -m "feat: 完成系统安全前端页面"
```

## Task 6: API Key Runtime Authentication

**Files:**
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/system/security/filter/ApiKeyAuthenticationFilter.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/system/security/service/ApiKeyAuthenticator.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/system/security/service/ApiKeyAuthAppService.java`
- Modify: `yudream-bootstrap/src/main/java/online/yudream/base/bootstrap/config/SaTokenConfigure.java`

**Steps:**
1. Read `X-API-Key` and `Authorization: Bearer yda_...` forms.
2. Extract prefix and hash candidate secret.
3. Validate enabled policy, credential status, expiry, and permission scope.
4. Bind permissions to request context in a way compatible with existing permission checks.
5. Update last used time asynchronously or after successful validation.
6. Verify normal user token login still works when API key is disabled.
7. Verify compile:

```powershell
$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'; $env:Path="$env:JAVA_HOME/bin;$env:Path"; mvn -pl yudream-bootstrap -am -DskipTests compile
```

8. Commit:

```powershell
git add yudream-domain/src/main/java/online/yudream/base/domain/system/security yudream-application/src/main/java/online/yudream/base/application/system/security yudream-interfaces/src/main/java/online/yudream/base/interfaces/system/security yudream-bootstrap/src/main/java/online/yudream/base/bootstrap/config/SaTokenConfigure.java
git commit -m "feat: 接入API Key运行时认证"
```

## Out of Scope for This Plan

- Full request/response encryption filter implementation.
- Full dual-token migration.
- OAuth server/client runtime.
- Passkey enrollment/login runtime.

These are represented in policy config and extension points first, then implemented in later plans.
