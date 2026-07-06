# User Role Dept Management Implementation Plan

> **For implementer:** Use TDD throughout. Write failing test first. Watch it fail. Then implement.

**Goal:** Build complete user, role, and department management across the existing DDD backend and Fantastic-admin frontend.

**Architecture:** Keep business rules in domain/application services, expose validated HTTP endpoints from interfaces, implement persistence through existing Mongo repositories, and replace frontend placeholder pages with real API-driven management screens. Preserve current login, registration, permission registration, dynamic menu, and current department/role context behavior.

**Tech Stack:** Java 21, Spring Boot 3.5, MongoTemplate, Sa-Token, Lombok, Vue 3, TypeScript, Vite, Arco Design Vue, Pinia, Fantastic-admin.

---

## Important Constraints

- Do not revert or clean unrelated working-tree changes.
- Keep package naming consistent with current code: `online.yudream.base`.
- Prefer logical disable/deactivate over physical delete.
- System roles and system/root departments are protected.
- Mutation endpoints must use existing permission codes and `@PermissionRegister`.
- Frontend must use the existing Arco/Fantastic-admin style and `v-auth` permissions.

## Task 1: Add Management Query, Command, and DTO Models

**Files:**

- Create: `yudream-application/src/main/java/online/yudream/base/application/system/user/cmd/UserCreateCmd.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/system/user/cmd/UserUpdateCmd.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/system/user/cmd/UserDeptAssignCmd.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/system/user/cmd/RoleCreateCmd.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/system/user/cmd/RoleUpdateCmd.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/system/user/cmd/DeptCreateCmd.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/system/user/cmd/DeptUpdateCmd.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/system/user/query/RolePageQuery.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/system/user/query/DeptTreeQuery.java`
- Create/modify DTOs for management rows and options.

**Steps:**

1. Write compile-oriented tests once the service APIs exist, or start with model compilation if no test harness exists yet.
2. Add serializable command/query classes with Lombok `@Data`.
3. Include fields needed by UI:
   - User: username, nickname, email, phone, qq, password, emailVerified, roleIds, depts.
   - Role: name, code, deptId, level, permissions, status.
   - Dept: name, description, leaderId, phone, parentId, sortOrder, status.
4. Run: `mvn -pl yudream-application -am test`.
5. Commit: `git add yudream-application/src/main/java/online/yudream/base/application/system/user && git commit -m "feat: add management command models"`.

## Task 2: Extend Domain Aggregates for Management Behavior

**Files:**

- Modify: `yudream-domain/src/main/java/online/yudream/base/domain/system/user/aggregate/User.java`
- Modify: `yudream-domain/src/main/java/online/yudream/base/domain/system/user/aggregate/Role.java`
- Modify: `yudream-domain/src/main/java/online/yudream/base/domain/system/user/aggregate/Dept.java`
- Modify if needed: `yudream-domain/src/main/java/online/yudream/base/domain/system/user/enumerate/*.java`

**Steps:**

1. Add unit tests for aggregate invariants when test dependencies are available from the Maven module; otherwise verify through application service tests in later tasks.
2. Add methods:
   - `User.updateProfile(...)`
   - `User.replaceRoles(List<RoleID>)`
   - `User.replaceDepts(List<UserDept>)`
   - `Role.updateBasic(...)`
   - `Role.replacePermissions(List<PermissionID>)`
   - `Role.activate()/deactivate()`
   - `Dept.updateBasic(...)`
   - `Dept.activate()/deactivate()`
3. Ensure `Role.deactivate()` refuses system roles.
4. Ensure `Dept.deactivate()` refuses root/system departments.
5. Run: `mvn -pl yudream-domain test`.
6. Commit: `git add yudream-domain/src/main/java/online/yudream/base/domain/system/user && git commit -m "feat: add management behavior to user domain"`.

## Task 3: Extend Repository Interfaces and Mongo Implementations

**Files:**

- Modify: `yudream-domain/src/main/java/online/yudream/base/domain/system/user/repo/UserRepo.java`
- Modify: `yudream-domain/src/main/java/online/yudream/base/domain/system/user/repo/RoleRepo.java`
- Modify: `yudream-domain/src/main/java/online/yudream/base/domain/system/user/repo/DeptRepo.java`
- Modify: `yudream-domain/src/main/java/online/yudream/base/domain/system/user/repo/PermissionRepo.java`
- Modify: `yudream-infrastructure/src/main/java/online/yudream/base/infra/system/user/impl/UserRepoImpl.java`
- Modify: `yudream-infrastructure/src/main/java/online/yudream/base/infra/system/user/impl/RoleRepoImpl.java`
- Modify: `yudream-infrastructure/src/main/java/online/yudream/base/infra/system/user/impl/DeptRepoImpl.java`
- Modify: `yudream-infrastructure/src/main/java/online/yudream/base/infra/system/user/impl/PermissionRepoImpl.java`

**Steps:**

1. Add repository contract methods for page/list/search/count/reference checks.
2. Implement Mongo queries with `MongoTemplate`.
3. Use `PageResult<T>` for paged data.
4. Add uniqueness checks excluding current ID where needed.
5. Keep all mapper usage centralized in existing infra mappers.
6. Run: `mvn -pl yudream-infrastructure -am test`.
7. Commit: `git add yudream-domain/src/main/java/online/yudream/base/domain/system/user/repo yudream-infrastructure/src/main/java/online/yudream/base/infra/system/user && git commit -m "feat: extend user management repositories"`.

## Task 4: Implement User Management Application Service

**Files:**

- Create: `yudream-application/src/main/java/online/yudream/base/application/system/user/service/UserManageAppService.java`
- Modify/Create: `yudream-application/src/main/java/online/yudream/base/application/system/user/assembler/UserAssembler.java`
- Create DTOs as needed under `yudream-application/src/main/java/online/yudream/base/application/system/user/dto/`.

**Steps:**

1. Write tests for:
   - create user rejects duplicate username/email.
   - update user keeps uniqueness excluding self.
   - assign roles rejects missing role IDs.
   - assign departments rejects missing departments and requires one default department.
2. Implement `page`, `create`, `update`, `disable`, `assignRoles`, `assignDepts`.
3. Resolve role and department display names for page rows.
4. Reuse `Password.of(..., passwordEncoder)` for created users.
5. Run targeted tests and `mvn -pl yudream-application -am test`.
6. Commit: `git add yudream-application/src/main/java/online/yudream/base/application/system/user && git commit -m "feat: add user management application service"`.

## Task 5: Implement Role Management Application Service

**Files:**

- Create: `yudream-application/src/main/java/online/yudream/base/application/system/user/service/RoleManageAppService.java`
- Create/Modify DTOs and assemblers under the existing user application package.

**Steps:**

1. Write tests for:
   - create role rejects duplicate code.
   - update system role protects code/dept/system-only fields.
   - deactivate system role fails.
   - deactivate role used by users fails.
   - assign permissions stores selected permission codes.
2. Implement page/list options/create/update/disable/assignPermissions.
3. Use active permission list for permission selector.
4. Run: `mvn -pl yudream-application -am test`.
5. Commit: `git add yudream-application/src/main/java/online/yudream/base/application/system/user && git commit -m "feat: add role management application service"`.

## Task 6: Implement Department Management Application Service

**Files:**

- Create: `yudream-application/src/main/java/online/yudream/base/application/system/user/service/DeptManageAppService.java`
- Create/Modify DTOs and assemblers under the existing user application package.

**Steps:**

1. Write tests for:
   - tree returns sorted hierarchy.
   - create child rejects missing parent.
   - update rejects parent cycle.
   - deactivate system/root department fails.
   - deactivate department with active children or users fails.
2. Implement tree/list options/create/update/disable.
3. Resolve leader nickname where a leader exists.
4. Run: `mvn -pl yudream-application -am test`.
5. Commit: `git add yudream-application/src/main/java/online/yudream/base/application/system/user && git commit -m "feat: add department management application service"`.

## Task 7: Add HTTP Request/Response Models and Controllers

**Files:**

- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/system/user/controller/UserManageController.java`
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/system/user/controller/RoleManageController.java`
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/system/user/controller/DeptManageController.java`
- Create request/response classes under `yudream-interfaces/src/main/java/online/yudream/base/interfaces/system/user/request/` and `.../res/`.
- Create/modify web assemblers as needed.

**Steps:**

1. Add controller tests if existing test setup supports Spring MVC; otherwise compile and manually inspect mappings.
2. Add endpoints:
   - `GET /api/system/users`
   - `POST /api/system/users`
   - `PUT /api/system/users/{id}`
   - `DELETE /api/system/users/{id}`
   - `PUT /api/system/users/{id}/roles`
   - `PUT /api/system/users/{id}/depts`
   - equivalent role and dept endpoints.
3. Annotate mutation endpoints with `@PermissionRegister`.
4. Keep responses wrapped in `Result.ok(...)`.
5. Run: `mvn -pl yudream-interfaces -am test`.
6. Commit: `git add yudream-interfaces/src/main/java/online/yudream/base/interfaces/system/user && git commit -m "feat: expose user role dept management APIs"`.

## Task 8: Add Frontend API Modules

**Files:**

- Create: `yudream-frontend/apps/core-arco-design-vue/src/api/modules/system-user.ts`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/api/modules/system-role.ts`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/api/modules/system-dept.ts`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/api/modules/system-permission.ts`

**Steps:**

1. Mirror the response handling style from `src/api/modules/user.ts`.
2. Define TypeScript interfaces for page records, forms, options, and tree nodes.
3. Add CRUD and assignment functions for all three modules.
4. Run: `pnpm --filter @fantastic-admin/core-arco-design-vue lint`.
5. Commit: `git add yudream-frontend/apps/core-arco-design-vue/src/api/modules && git commit -m "feat: add system management api modules"`.

## Task 9: Build User Management Page

**Files:**

- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/system/user/index.vue`

**Steps:**

1. Replace placeholder with a compact management view.
2. Add search form, table, pagination, loading state, and create/edit modal or drawer.
3. Add role and department assignment controls.
4. Gate buttons with:
   - `v-auth="'system:user:create'"`
   - `v-auth="'system:user:edit'"`
   - `v-auth="'system:user:delete'"`
5. Refresh list after mutations.
6. Run frontend lint/build verification.
7. Commit: `git add yudream-frontend/apps/core-arco-design-vue/src/views/system/user/index.vue && git commit -m "feat: build user management page"`.

## Task 10: Build Role Management Page

**Files:**

- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/system/role/index.vue`

**Steps:**

1. Replace placeholder with role list/search/table.
2. Add create/edit form.
3. Add permission assignment tree/checkbox UI.
4. Disable protected actions for system roles.
5. Gate buttons with `system:role:*` permissions.
6. Run frontend lint/build verification.
7. Commit: `git add yudream-frontend/apps/core-arco-design-vue/src/views/system/role/index.vue && git commit -m "feat: build role management page"`.

## Task 11: Build Department Management Page

**Files:**

- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/system/dept/index.vue`

**Steps:**

1. Replace placeholder with tree table.
2. Add create child/edit form.
3. Add parent selector and leader selector.
4. Disable protected actions for root/system departments.
5. Gate buttons with `system:dept:*` permissions.
6. Run frontend lint/build verification.
7. Commit: `git add yudream-frontend/apps/core-arco-design-vue/src/views/system/dept/index.vue && git commit -m "feat: build department management page"`.

## Task 12: Full Verification and Integration Fixes

**Files:**

- Modify only files touched by previous tasks if verification finds defects.

**Steps:**

1. Run backend verification: `mvn test`.
2. Run frontend verification from `yudream-frontend`: `pnpm --filter @fantastic-admin/core-arco-design-vue lint`.
3. If practical, run the backend and frontend dev server and smoke-test:
   - login
   - menu visibility
   - user page list/create/edit/assign/deactivate
   - role page list/create/edit/permissions/deactivate
   - dept page tree/create/edit/deactivate
4. Fix defects with focused commits.
5. Final commit if any integration fixes were needed.
