# File, Profile, and System Settings Design

## Context

The backend already follows a DDD-style module layout:

- `yudream-domain` owns aggregates, repository interfaces, and domain ports.
- `yudream-application` coordinates use cases.
- `yudream-infrastructure` implements MongoDB, Redis, Sa-Token, and other framework adapters.
- `yudream-interfaces` exposes HTTP controllers and request/response objects.

The frontend is a Fantastic-admin Vue app at `yudream-frontend/apps/core-arco-design-vue`.

Existing setting support is read-only and public:

- `Setting` aggregate stores key/value/type/category/description.
- `SettingAppService.publicSettings()` returns all settings.
- `GET /api/settings/public` is used by the frontend settings store.

The first implementation phase will add RustFS-backed S3-compatible object storage, avatar upload/display/change, current-user profile editing, and system settings management for basic site information.

## Goals

- Connect to RustFS using the S3-compatible API.
- Use bucket `yudream-admin`.
- Upload files through the backend instead of exposing RustFS directly.
- Store file metadata in MongoDB.
- Let users upload, display, and replace their avatars.
- Let users view and update their own profile fields.
- Let administrators maintain site name, logo, favicon, copyright, company, website, and basic description settings.
- Let frontend load and apply public settings at startup.

## Non-Goals

- Redis monitor, API log list, login log list, and online user monitor are phase two.
- No direct public RustFS bucket requirement.
- No object lifecycle management beyond metadata and logical deletion in phase one.
- No image processing or thumbnail generation in phase one.

## Architecture

### File Storage

Add a domain port for object storage and a file metadata aggregate:

- `FileObject` stores id, bucket, objectKey, originalName, contentType, size, module, uploaderId, publicAccess, deleted.
- `ObjectStorage` uploads, downloads, and deletes raw objects.
- `FileObjectRepo` persists metadata.

Infrastructure implements `ObjectStorage` with AWS SDK v2 S3 client configured for RustFS:

- endpoint: `http://localhost:9000`
- access key: `rustfs`
- secret key: `rustfs`
- bucket: `yudream-admin`
- path-style access enabled

Interfaces expose:

- `POST /api/files/upload`
- `GET /api/files/{id}/content`
- `DELETE /api/files/{id}`

### User Profile and Avatar

Extend `User` with `avatarFileId` and `avatarUrl`.

- `avatarFileId` points to backend-managed file metadata.
- `avatarUrl` is the display URL returned to frontend, derived as `/api/files/{avatarFileId}/content`.

Current-user APIs:

- `GET /api/user/me/profile`
- `PUT /api/user/me/profile`
- `POST /api/user/me/avatar`

The avatar upload endpoint uploads to file storage and updates the current user.

### System Settings

Keep `Setting` as the aggregate but add managed update use cases:

- `GET /api/system/settings/site`
- `PUT /api/system/settings/site`
- `POST /api/system/settings/site/logo`
- `POST /api/system/settings/site/favicon`

Public settings remain available through `/api/settings/public`. Private/admin settings use `@PermissionRegister` with system setting permissions.

### Frontend

Add API modules:

- `system-file.ts`
- `profile.ts`
- extend `settings.ts`

Add views:

- `views/profile/index.vue`
- `views/system/setting/index.vue`

Update account UI:

- login response and account store include avatar URL.
- account button displays avatar when present.
- profile page supports upload/change and profile field editing.

## Error Handling

- Missing files return a business error.
- Upload validates non-empty file and content length.
- Avatar upload accepts image files only.
- S3/RustFS errors are wrapped in a business/infrastructure exception with a concise message.
- Settings update validates required site name and valid URL-like fields where reasonable.

## Testing Strategy

- Domain tests for file metadata deletion and user avatar/profile mutation.
- Application tests with fake `ObjectStorage` and fake repositories where practical.
- Compile-level backend verification with Maven.
- Frontend `vue-tsc` verification, accepting the pre-existing `src/iconify/index.ts` type issue as unrelated.
