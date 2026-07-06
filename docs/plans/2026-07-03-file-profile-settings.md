# File Profile Settings Implementation Plan

> **For implementer:** Use TDD throughout. Write failing test first. Watch it fail. Then implement.

**Goal:** Build the first-phase file storage, avatar/profile, and system settings management loop.

**Architecture:** Add domain ports and metadata for file objects, implement S3-compatible storage against RustFS in infrastructure, expose upload/content/profile/settings APIs from interfaces, and build Fantastic-admin pages for profile and site settings. File display goes through the backend rather than direct RustFS URLs.

**Tech Stack:** Java 21, Spring Boot 3.5, MongoDB, AWS SDK v2 S3, Sa-Token, Vue 3, TypeScript, Fantastic-admin, Arco fallback components.

---

## Task 1: Add File Domain Model and Ports

**Files:**

- Create: `yudream-domain/src/main/java/online/yudream/base/domain/system/file/aggregate/FileObject.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/system/file/repo/FileObjectRepo.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/system/file/service/ObjectStorage.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/system/file/valobj/StoredObject.java`

**Verify:**

- Run: `mvn -pl yudream-domain test`
- Expected: PASS when Java 21 is available.

## Task 2: Implement RustFS S3 Adapter and Mongo Metadata Repo

**Files:**

- Modify: `pom.xml`
- Modify: `yudream-infrastructure/pom.xml`
- Modify: `yudream-bootstrap/src/main/resources/application.yml`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/system/file/config/S3StorageProperties.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/system/file/config/S3StorageConfig.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/system/file/service/S3ObjectStorage.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/system/file/dataobj/FileObjectDO.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/system/file/mapper/FileObjectInfraMapper.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/system/file/impl/FileObjectRepoImpl.java`

**Verify:**

- Run: `mvn -pl yudream-infrastructure -am test`
- Expected: PASS when Java 21 is available.

## Task 3: Add File Application and HTTP APIs

**Files:**

- Create: `yudream-application/src/main/java/online/yudream/base/application/system/file/dto/FileObjectDTO.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/system/file/service/FileAppService.java`
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/system/file/controller/FileController.java`

**Verify:**

- Upload a small image through `POST /api/files/upload`.
- Read it through `GET /api/files/{id}/content`.

## Task 4: Extend User Profile and Avatar

**Files:**

- Modify: `yudream-domain/src/main/java/online/yudream/base/domain/system/user/aggregate/User.java`
- Modify: `yudream-infrastructure/src/main/java/online/yudream/base/infra/system/user/dataobj/UserDO.java`
- Modify: `yudream-infrastructure/src/main/java/online/yudream/base/infra/system/user/mapper/UserInfraMapper.java`
- Modify: `yudream-application/src/main/java/online/yudream/base/application/system/user/service/UserAppService.java`
- Modify: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/system/user/controller/UserController.java`
- Create request/response DTOs for profile update and avatar response.

**Verify:**

- `GET /api/user/me/profile` returns current profile and avatar URL.
- `PUT /api/user/me/profile` updates nickname, phone, QQ.
- `POST /api/user/me/avatar` uploads an image and updates avatar.

## Task 5: Add Managed Site Settings APIs

**Files:**

- Modify: `yudream-application/src/main/java/online/yudream/base/application/system/setting/service/SettingAppService.java`
- Modify: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/system/setting/controller/SettingController.java`
- Create: site setting request/response classes.

**Verify:**

- `GET /api/system/settings/site` returns editable site settings.
- `PUT /api/system/settings/site` persists text fields.
- logo/favicon upload endpoints store files and set public setting URLs.

## Task 6: Frontend Profile and Settings Pages

**Files:**

- Create: `yudream-frontend/apps/core-arco-design-vue/src/api/modules/system-file.ts`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/api/modules/profile.ts`
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/api/modules/settings.ts`
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/store/modules/app/account.ts`
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/components/AppAccountButton/index.vue`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/views/profile/index.vue`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/views/system/setting/index.vue`

**Verify:**

- Run: `pnpm --config.engine-strict=false --filter @fantastic-admin/core-arco-design-vue lint`
- Expected: no new errors except the existing `src/iconify/index.ts` `IconifyJSON` issue.

## Execution Mode

The current toolset does not expose `sessions_spawn`, so implementation proceeds manually in this session with frequent verification and small scoped edits.
