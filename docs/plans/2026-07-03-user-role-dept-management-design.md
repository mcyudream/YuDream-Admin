# User, Role, and Department Management Design

## Context

The project already has a layered DDD backend:

- `yudream-domain` owns aggregates, value objects, repository interfaces, and domain services.
- `yudream-application` coordinates use cases through application services, commands, queries, and DTOs.
- `yudream-infrastructure` implements MongoDB repositories and framework integrations.
- `yudream-interfaces` exposes HTTP controllers and request/response models.

The frontend is a Fantastic-admin Vue 3 application under `yudream-frontend/apps/core-arco-design-vue`. It already has placeholder pages for:

- `views/system/user/index.vue`
- `views/system/role/index.vue`
- `views/system/dept/index.vue`

The backend also already initializes system departments and roles, registers menu nodes for user/role/dept management, and supports permission registration through `@PermissionRegister`.

## Goals

Build a complete management loop for users, roles, and departments:

- Users can be searched, paged, created, edited, assigned roles, assigned departments, and disabled.
- Roles can be searched, created, edited, assigned permissions, enabled/disabled, and protected when they are system roles.
- Departments can be displayed as a tree, created, edited, enabled/disabled, and protected when they are system departments or have active children/users.
- Frontend pages consume real APIs and respect existing `v-auth` button permissions.
- Backend follows the existing DDD boundaries instead of putting business rules in controllers or Mongo objects.

## Non-Goals

- Do not redesign login, registration, email verification, current department switching, or current role switching.
- Do not replace the existing permission/menu registration system.
- Do not introduce a new frontend UI framework.
- Do not physically delete business records by default. Delete actions are implemented as logical disable/deactivate operations where the aggregate supports status.

## Recommended Architecture

### Domain Layer

Extend existing aggregates with management behavior:

- `User`
  - Update editable profile fields: nickname, email, phone, QQ.
  - Verify uniqueness-sensitive changes through application/domain service coordination.
  - Replace role assignments.
  - Replace department assignments, keeping exactly one default department when departments exist.
  - Support disabling through a user status field if the current model adds one during implementation.

- `Role`
  - Update name, code, department, level, and permissions.
  - Activate/deactivate.
  - Refuse destructive changes to system roles where appropriate.

- `Dept`
  - Update name, description, leader, phone, parent, sort order, and status.
  - Refuse invalid parent cycles.
  - Refuse destructive changes to system/root departments.

Repository interfaces will be expanded only for use cases needed by management:

- `UserRepo`: page search, count by dept/role, uniqueness checks excluding current user, delete/disable support if required.
- `RoleRepo`: page/list search, find by dept, uniqueness checks excluding current role.
- `DeptRepo`: find all, find children, uniqueness checks under parent, count children or references.
- `PermissionRepo`: expose active permissions for the role permission selector if not already present.

### Application Layer

Add dedicated management services instead of overloading login/context services:

- `UserManageAppService`
  - `page(UserPageQuery)`
  - `create(UserCreateCmd)`
  - `update(UserUpdateCmd)`
  - `disable(Long id)` or `delete(Long id)` with logical semantics
  - `assignRoles(Long userId, List<Long> roleIds)`
  - `assignDepts(Long userId, List<UserDeptAssignCmd>)`

- `RoleManageAppService`
  - `page(RolePageQuery)`
  - `listOptions()`
  - `create(RoleCreateCmd)`
  - `update(RoleUpdateCmd)`
  - `disable(Long id)`
  - `assignPermissions(Long roleId, List<String> permissionCodes)`

- `DeptManageAppService`
  - `tree(DeptTreeQuery)`
  - `listOptions()`
  - `create(DeptCreateCmd)`
  - `update(DeptUpdateCmd)`
  - `disable(Long id)`

DTOs should include display names resolved for table rendering:

- User table rows include role names and department names.
- Role rows include department name and permission count.
- Department tree rows include leader nickname when available.

### Interfaces Layer

Expose management endpoints under stable API prefixes:

- `/api/system/users`
- `/api/system/roles`
- `/api/system/depts`
- `/api/system/permissions`

Controllers must:

- Use request classes for validation.
- Delegate all business decisions to application services.
- Annotate mutation endpoints with `@PermissionRegister`.
- Use the existing `Result<T>` wrapper.

Permission codes follow existing menu/button definitions:

- `system:user:create`
- `system:user:edit`
- `system:user:delete`
- `system:role:create`
- `system:role:edit`
- `system:role:delete`
- `system:dept:create`
- `system:dept:edit`
- `system:dept:delete`

Read endpoints may either require the parent menu permission codes or stay authenticated-only depending on the existing menu visibility behavior. The safer default is to require `system:user`, `system:role`, and `system:dept` for list/detail reads if those permissions are granted through roles.

### Infrastructure Layer

Mongo repository implementations should use `MongoTemplate` consistently with the existing code:

- Page queries use `Query`, `Criteria`, `skip`, `limit`, and `count`.
- Fuzzy search uses regex criteria for username/nickname/code/name where appropriate.
- Tree queries sort by `sortOrder` and then create time/id.
- Save methods preserve `createTime`, update `updateTime`, and keep existing ID generation patterns.

Avoid leaking Mongo data objects into application DTOs.

### Frontend

Add API modules:

- `src/api/modules/system-user.ts`
- `src/api/modules/system-role.ts`
- `src/api/modules/system-dept.ts`
- optionally `src/api/modules/system-permission.ts`

Replace placeholders with production management screens:

- User page
  - Search bar: keyword, department, role, status.
  - Table: username, nickname, email, departments, roles, email verified, created time, status, actions.
  - Drawer/modal form for create/edit.
  - Role and department assignment controls.
  - Buttons gated by `v-auth`.

- Role page
  - Search bar: keyword, department, status.
  - Table: name, code, department, level, permission count, system flag, status, actions.
  - Form for create/edit.
  - Permission tree/checkbox assignment.
  - System role fields are protected in the UI.

- Department page
  - Tree table: name, leader, phone, sort, status, actions.
  - Create child department and edit department forms.
  - Parent selector prevents choosing self/descendants.
  - System/root department actions are disabled.

The UI should stay work-focused: compact headers, search/tool bars, tables, drawers/modals, and no marketing/hero layout.

## Data Flow

1. User opens a management page.
2. Frontend route is visible only if backend menu/permission routing grants access.
3. Page fetches list/tree data through the relevant API module.
4. User performs create/edit/disable/assign action.
5. Controller validates request and checks permission through `@PermissionRegister`.
6. Application service loads aggregates, validates invariants, and saves through repositories.
7. Frontend refreshes the current table/tree and shows a toast.

## Error Handling

- Domain/application validation failures throw `BizException` with user-readable Chinese messages.
- Controllers rely on existing global exception handling.
- Frontend uses existing toast handling and keeps forms open on validation or business failures.
- Deactivation errors should be explicit, for example:
  - `系统角色不可停用`
  - `部门存在启用的子部门，不能停用`
  - `角色仍被用户使用，不能停用`

## Testing Strategy

Backend:

- Add focused application service tests for:
  - User create/update uniqueness.
  - Assigning roles/departments.
  - Role system protection and permission assignment.
  - Department tree and parent-cycle protection.
- Add repository tests where Mongo query behavior is non-trivial.
- Always run Maven compilation/tests for changed modules.

Frontend:

- Run `pnpm --filter @fantastic-admin/core-arco-design-vue lint` or the app build script after page/API changes.
- Verify the three pages render, text fits, actions are permission-gated, and table/tree loading states are sane.

## Implementation Order

1. Backend query and command models.
2. Repository interface and Mongo implementation extensions.
3. Domain aggregate behavior and protection rules.
4. Application management services and tests.
5. HTTP request/response/assembler/controller classes with permissions.
6. Frontend API modules.
7. Frontend user, role, and department pages.
8. Full backend and frontend verification.
