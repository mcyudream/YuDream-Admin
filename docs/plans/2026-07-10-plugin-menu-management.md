# 插件菜单统一管理 Implementation Plan

> **For implementer:** Use TDD throughout. Write failing test first. Watch it fail. Then implement.

**Goal:** 将插件注册菜单纳入系统菜单管理，支持完整编辑、跨系统树挂载，并在插件卸载后隐藏、重新安装后恢复配置。

**Architecture:** 使用 `sysMenu` 作为系统菜单和插件菜单的统一持久化模型，以节点自身的插件来源标记控制生命周期。插件运行时声明只提供首次默认值，动态前端清单应用持久化菜单覆盖。

**Tech Stack:** Java 21, Spring Boot, MongoDB, JUnit 5, Mockito, Vue 3, TypeScript, Pinia/Vue Router

---

### Task 1: 扩展菜单来源和插件归属模型

**Files:**
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/system/menu/enumerate/MenuSource.java`
- Modify: `yudream-domain/src/main/java/online/yudream/base/domain/system/menu/aggregate/Menu.java`
- Modify: `yudream-infrastructure/src/main/java/online/yudream/base/infra/system/menu/dataobj/MenuDO.java`
- Modify: `yudream-infrastructure/src/main/java/online/yudream/base/infra/system/menu/mapper/MenuInfraMapper.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/domain/system/menu/MenuPluginMetadataTest.java`

**Step 1: Write the failing test**

```java
@Test
void pluginOwnershipDoesNotDependOnParent() {
    Menu menu = Menu.builder()
            .code("plugin:wallet:home")
            .parentCode("system:dashboard")
            .source(MenuSource.PLUGIN)
            .pluginCode("yudream-wallet")
            .pluginRegistrationKey("route:yudreamWallet:platform-plugin-yudream-wallet")
            .runtimeAvailable(false)
            .build();

    assertThat(menu.isPluginMenu()).isTrue();
    assertThat(menu.isRuntimeAvailable()).isFalse();
}
```

**Step 2: Run test and confirm RED**

Command: `$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'; mvn -pl yudream-bootstrap -am -Dtest=MenuPluginMetadataTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because `MenuSource` and plugin metadata do not exist.

**Step 3: Write minimal implementation**

Add `MenuSource { SYSTEM, PLUGIN }`, persist the five fields from the design, default missing `source` to `SYSTEM`, and add:

```java
public boolean isPluginMenu() {
    return source == MenuSource.PLUGIN;
}

public boolean isRuntimeAvailable() {
    return !isPluginMenu() || Boolean.TRUE.equals(runtimeAvailable);
}
```

**Step 4: Run test and confirm GREEN**

Run the command from Step 2. Expected: PASS.

**Step 5: Commit**

`git add yudream-domain yudream-infrastructure yudream-bootstrap/src/test && git commit -m "feat: 扩展插件菜单归属模型"`

### Task 2: 投影插件声明并保留管理员配置

**Files:**
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/plugin/service/PluginMenuProjectionService.java`
- Modify: `yudream-domain/src/main/java/online/yudream/base/domain/system/menu/repo/MenuRepo.java`
- Modify: `yudream-infrastructure/src/main/java/online/yudream/base/infra/system/menu/impl/MenuRepoImpl.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/application/platform/plugin/PluginMenuProjectionServiceTest.java`

**Step 1: Write failing tests**

Use Mockito-backed `MenuRepo` tests for these individual behaviours:

```java
@Test void createsModuleParentAndRouteMenusFromFrontendDeclaration() { }
@Test void keepsEditedFieldsWhenTheSameRegistrationReturns() { }
@Test void marksRemovedDeclarationsUnavailableWithoutDeletingThem() { }
@Test void preservesAParentThatPointsToASystemMenu() { }
```

The preservation assertion must include edited `name`, `icon`, `path`, `component`, `permission`, `parentCode`, `sort`, `visible`, and `status`.

**Step 2: Run tests and confirm RED**

Command: `$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'; mvn -pl yudream-bootstrap -am -Dtest=PluginMenuProjectionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because projection service and repository queries are absent.

**Step 3: Write minimal implementation**

Add repository methods:

```java
Optional<Menu> findByPluginCodeAndRegistrationKey(String pluginCode, String registrationKey);
List<Menu> findByPluginCode(String pluginCode);
```

Projection rules:

```java
String moduleKey = "module:" + module.moduleName();
String parentKey = "parent:" + module.moduleName() + ":" + declaredParentPath;
String routeKey = "route:" + module.moduleName() + ":" + stableRouteName;
```

On insert, copy declaration defaults. On match, retain every editable field and only refresh ownership metadata plus `runtimeAvailable=true`. After syncing current keys, set unmatched records for the plugin to `runtimeAvailable=false`.

**Step 4: Run tests and confirm GREEN**

Run the command from Step 2. Expected: PASS.

**Step 5: Commit**

`git add yudream-application yudream-domain yudream-infrastructure yudream-bootstrap/src/test && git commit -m "feat: 持久化插件菜单注册投影"`

### Task 3: 接入插件生命周期和动态前端清单

**Files:**
- Modify: `yudream-application/src/main/java/online/yudream/base/application/platform/plugin/service/PluginAppService.java`
- Modify: `yudream-application/src/main/java/online/yudream/base/application/platform/plugin/assembler/PluginAssembler.java`
- Modify: `yudream-domain/src/main/java/online/yudream/base/domain/platform/plugin/valobj/PluginFrontendModuleInfo.java`
- Modify: `yudream-domain/src/main/java/online/yudream/base/domain/platform/plugin/valobj/PluginFrontendRouteInfo.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/application/platform/plugin/PluginMenuLifecycleTest.java`

**Step 1: Write failing tests**

```java
@Test void enableProjectsMenusAfterRuntimeEnableSucceeds() { }
@Test void disableUnloadAndDeleteOnlyHidePluginMenus() { }
@Test void reinstallAppliesPersistedOverridesToFrontendManifest() { }
@Test void disabledMenuIsExcludedAndInvisibleMenuRemainsRoutable() { }
```

Verify lifecycle calls occur after successful runtime operations. Verify delete no longer removes menu projection records.

**Step 2: Run tests and confirm RED**

Command: `$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'; mvn -pl yudream-bootstrap -am -Dtest=PluginMenuLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because lifecycle synchronization and full overrides are absent.

**Step 3: Write minimal implementation**

- Inject `PluginMenuProjectionService` into `PluginAppService`.
- After `enableRuntimeWithDependencies` succeeds, project the enabled plugin frontend modules.
- On `disable`, `unload`, and `delete`, mark menus unavailable by `pluginCode`.
- Keep menu projection records even when `PluginModule` and JAR are removed.
- Build frontend manifest from enabled runtime modules plus persisted menu overrides.
- Carry `parentCode`, `visible`, and `status` in internal frontend route metadata needed by the frontend merger.

**Step 4: Run tests and confirm GREEN**

Run the command from Step 2. Expected: PASS.

**Step 5: Commit**

`git add yudream-application yudream-domain yudream-bootstrap/src/test && git commit -m "feat: 同步插件生命周期与菜单配置"`

### Task 4: 在菜单管理接口展示并编辑插件菜单

**Files:**
- Modify: `yudream-application/src/main/java/online/yudream/base/application/system/menu/service/MenuAppService.java`
- Modify: `yudream-application/src/main/java/online/yudream/base/application/system/menu/dto/MenuManageDTO.java`
- Modify: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/system/menu/res/MenuManageRes.java`
- Modify: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/system/menu/assembler/MenuWebAssembler.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/application/system/menu/MenuAppServicePluginMenuTest.java`

**Step 1: Write failing tests**

```java
@Test void managementTreeShowsAvailablePluginMenusUnderSystemParents() { }
@Test void managementTreeHidesUnavailablePluginMenus() { }
@Test void staticRouteTreeNeverReturnsPluginMenus() { }
@Test void updateKeepsPluginOwnershipMetadata() { }
```

**Step 2: Run tests and confirm RED**

Command: `$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'; mvn -pl yudream-bootstrap -am -Dtest=MenuAppServicePluginMenuTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because management filtering and source DTO fields are absent.

**Step 3: Write minimal implementation**

- `tree()` filters only unavailable plugin nodes; their system parents remain.
- `buildRouteTree()` filters all `source=PLUGIN` nodes.
- `update()` changes editable fields without touching plugin ownership metadata.
- DTO and response expose `source`, `pluginCode`, `pluginModuleName`, and `runtimeAvailable`.
- Keep existing parent existence and cycle checks so cross-source parenting works safely.

**Step 4: Run tests and confirm GREEN**

Run the command from Step 2. Expected: PASS.

**Step 5: Commit**

`git add yudream-application yudream-interfaces yudream-bootstrap/src/test && git commit -m "feat: 菜单管理支持插件菜单编辑"`

### Task 5: 前端显示来源并合并跨树插件路由

**Files:**
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/api/modules/system-menu.ts`
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/api/modules/platform-plugin.ts`
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/system/menu/index.vue`
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/store/modules/app/route.ts`

**Step 1: Establish failing verification**

Add the new TypeScript fields to usages first and run:

`pnpm --config.engine-strict=false --filter @fantastic-admin/core-arco-design-vue exec vue-tsc --noEmit --pretty false --skipLibCheck --ignoreDeprecations 6.0`

Expected: FAIL until API types and route merger are updated consistently.

**Step 2: Write minimal implementation**

- Add menu source and plugin metadata types.
- Display a compact “插件” source tag in the menu tree/detail view and keep plugin menu code disabled in edit mode.
- Continue allowing all editable fields and all parent options.
- Merge plugin routes by persisted `parentCode`: when it names a system category/directory, attach the plugin route to that existing group; otherwise retain the plugin module group fallback.
- Respect `visible=false` with `meta.menu=false`; omit disabled or runtime-unavailable plugin nodes.

**Step 3: Run type verification**

Run the command from Step 1. Expected: PASS.

**Step 4: Commit**

`git add yudream-frontend/apps/core-arco-design-vue/src && git commit -m "feat: 前端统一展示插件菜单"`

### Task 6: 全量验证和架构检查

**Files:**
- Verify only; modify production files only for failures directly caused by this feature.

**Step 1: Run targeted backend tests**

`$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'; mvn -pl yudream-bootstrap -am -Dtest=MenuPluginMetadataTest,PluginMenuProjectionServiceTest,PluginMenuLifecycleTest,MenuAppServicePluginMenuTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS.

**Step 2: Compile backend**

`$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'; $env:Path="$env:JAVA_HOME/bin;$env:Path"; mvn -pl yudream-bootstrap -am -DskipTests compile`

Expected: BUILD SUCCESS.

**Step 3: Run frontend type check**

`pnpm --config.engine-strict=false --filter @fantastic-admin/core-arco-design-vue exec vue-tsc --noEmit --pretty false --skipLibCheck --ignoreDeprecations 6.0`

Expected: exit code 0.

**Step 4: Run architecture scans**

```powershell
rg -n "private .*to[A-Z]|new .*Cmd|new .*ExcelRow|\.builder\(\)" yudream-interfaces/src/main/java/online/yudream/base/interfaces -g "*Controller.java"
rg -n "\\u[0-9a-fA-F]{4}|\?\)" yudream-domain/src/main/java yudream-application/src/main/java yudream-infrastructure/src/main/java yudream-interfaces/src/main/java yudream-bootstrap/src/main/java -g "*.java"
```

Expected: no new violations in changed files.

**Step 5: Final review commit if needed**

Only when verification requires scoped fixes: `git add <feature-files> && git commit -m "fix: 完善插件菜单统一管理验证"`

