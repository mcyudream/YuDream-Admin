package online.yudream.base.application.platform.plugin;

import online.yudream.base.application.platform.plugin.cmd.PluginFrontendRouteSortSaveCmd;
import online.yudream.base.application.platform.plugin.cmd.PluginFrontendSortSaveCmd;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendManifestDTO;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendModuleDTO;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendRouteDTO;
import online.yudream.base.application.platform.plugin.service.PluginAppService;
import online.yudream.base.application.platform.plugin.service.PluginMenuProjectionService;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.enumerate.PluginStatus;
import online.yudream.base.domain.platform.plugin.repo.PluginModuleRepo;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendModuleInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendRouteInfo;
import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.domain.system.menu.repo.MenuRepo;
import online.yudream.base.domain.system.user.service.PermissionDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginMenuLifecycleTest {

    private static final String PLUGIN_CODE = "yudream-wallet";
    private static final String MODULE_NAME = "walletAdmin";

    @TempDir
    Path tempDir;

    @Mock
    private PluginModuleRepo pluginModuleRepo;

    @Mock
    private PluginRuntimeGateway pluginRuntimeGateway;

    @Mock
    private PermissionDomainService permissionDomainService;

    @Mock
    private PluginMenuProjectionService pluginMenuProjectionService;

    private PluginModule module;
    private PluginFrontendModuleInfo frontendModule;
    private PluginAppService service;

    @BeforeEach
    void setUp() throws Exception {
        Path jar = Files.createFile(tempDir.resolve("wallet.jar"));
        module = PluginModule.builder()
                .code(PLUGIN_CODE)
                .name("钱包插件")
                .pluginVersion("1.0.0")
                .jarPath(jar.toString())
                .dependencies(List.of())
                .build();
        frontendModule = frontendModule();
        service = new PluginAppService(
                pluginModuleRepo,
                pluginRuntimeGateway,
                permissionDomainService,
                pluginMenuProjectionService
        );
    }

    @Test
    void enableProjectsMenusAfterRuntimeEnableSucceeds() {
        stubEnable();

        service.enable(PLUGIN_CODE);

        InOrder order = inOrder(pluginRuntimeGateway, pluginMenuProjectionService);
        order.verify(pluginRuntimeGateway).enable(module);
        order.verify(pluginMenuProjectionService).project(PLUGIN_CODE, List.of(frontendModule));
    }

    @Test
    void enablingDependencyProjectsItsMenusAfterRuntimeEnableSucceeds() throws Exception {
        String dependencyCode = "yudream-ledger";
        Path dependencyJar = Files.createFile(tempDir.resolve("ledger.jar"));
        PluginModule dependency = PluginModule.builder()
                .code(dependencyCode)
                .name("账本插件")
                .pluginVersion("1.0.0")
                .jarPath(dependencyJar.toString())
                .dependencies(List.of())
                .build();
        dependency.markEnabled();
        module.setDependencies(List.of(dependencyCode));
        PluginFrontendModuleInfo dependencyFrontend = new PluginFrontendModuleInfo(
                dependencyCode,
                "/api/platform/plugins/yudream-ledger/assets/remoteEntry.js",
                "ledgerAdmin",
                "1.0.0",
                "sha256-ledger",
                List.of()
        );
        when(pluginModuleRepo.findAll()).thenReturn(List.of(module, dependency));
        when(pluginModuleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pluginRuntimeGateway.frontendModules()).thenReturn(List.of(frontendModule, dependencyFrontend));
        when(pluginRuntimeGateway.permissions(any())).thenReturn(List.of());
        Set<String> runtimeEnabled = new HashSet<>();
        doAnswer(invocation -> {
            runtimeEnabled.add(invocation.<PluginModule>getArgument(0).getCode());
            return null;
        }).when(pluginRuntimeGateway).enable(any());
        when(pluginRuntimeGateway.enabled(any())).thenAnswer(invocation -> runtimeEnabled.contains(invocation.getArgument(0)));

        service.enable(PLUGIN_CODE);

        InOrder order = inOrder(pluginRuntimeGateway, pluginMenuProjectionService);
        order.verify(pluginRuntimeGateway).enable(dependency);
        order.verify(pluginMenuProjectionService).project(dependencyCode, List.of(dependencyFrontend));
        order.verify(pluginRuntimeGateway).enable(module);
        order.verify(pluginMenuProjectionService).project(PLUGIN_CODE, List.of(frontendModule));
    }

    @Test
    void dependencyProjectionFailureCleansDependencyRuntimeAndMenus() throws Exception {
        String dependencyCode = "yudream-ledger";
        Path dependencyJar = Files.createFile(tempDir.resolve("ledger-failure.jar"));
        PluginModule dependency = PluginModule.builder()
                .code(dependencyCode)
                .name("账本插件")
                .pluginVersion("1.0.0")
                .jarPath(dependencyJar.toString())
                .dependencies(List.of())
                .build();
        dependency.markEnabled();
        module.setDependencies(List.of(dependencyCode));
        PluginFrontendModuleInfo dependencyFrontend = new PluginFrontendModuleInfo(
                dependencyCode,
                "/api/platform/plugins/yudream-ledger/assets/remoteEntry.js",
                "ledgerAdmin",
                "1.0.0",
                "sha256-ledger",
                List.of()
        );
        InMemoryMenuRepo menuRepo = new InMemoryMenuRepo();
        new PluginMenuProjectionService(menuRepo).project(dependencyCode, List.of(dependencyFrontend));
        PluginMenuProjectionService failingProjection = new FailingProjectionService(menuRepo, dependencyCode);
        PluginAppService realProjectionService = appService(failingProjection);
        Set<String> runtimeEnabled = statefulRuntimeEnable();
        when(pluginModuleRepo.findAll()).thenReturn(List.of(module, dependency));
        when(pluginModuleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pluginRuntimeGateway.frontendModules()).thenReturn(List.of(frontendModule, dependencyFrontend));
        when(pluginRuntimeGateway.permissions(any())).thenReturn(List.of());

        assertThatThrownBy(() -> realProjectionService.enable(PLUGIN_CODE))
                .hasMessageContaining("插件启用失败");

        assertThat(dependency.getStatus()).isEqualTo(PluginStatus.ERROR);
        assertThat(dependency.getErrorMessage()).contains("projection failed");
        assertThat(runtimeEnabled).doesNotContain(dependencyCode);
        assertThat(menuRepo.findByPluginCode(dependencyCode))
                .extracting(Menu::getRuntimeAvailable)
                .containsOnly(false);
    }

    @Test
    void failedEnableDoesNotExposeMenus() {
        when(pluginModuleRepo.findAll()).thenReturn(List.of(module));
        doThrow(new IllegalStateException("enable failed")).when(pluginRuntimeGateway).enable(module);

        assertThatThrownBy(() -> service.enable(PLUGIN_CODE))
                .hasMessageContaining("插件启用失败");

        verify(pluginMenuProjectionService, never()).project(any(), any());
    }

    @Test
    void failedEnableMarksPreExistingMenusUnavailable() {
        InMemoryMenuRepo menuRepo = projectedMenus(frontendModule);
        PluginMenuProjectionService projectionService = new PluginMenuProjectionService(menuRepo);
        PluginAppService realProjectionService = appService(projectionService);
        when(pluginModuleRepo.findAll()).thenReturn(List.of(module));
        when(pluginModuleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pluginRuntimeGateway.frontendModules()).thenReturn(List.of(frontendModule));
        doThrow(new IllegalStateException("enable failed")).when(pluginRuntimeGateway).enable(module);

        assertThatThrownBy(() -> realProjectionService.enable(PLUGIN_CODE))
                .hasMessageContaining("插件启用失败");

        assertThat(menuRepo.findByPluginCode(PLUGIN_CODE))
                .extracting(Menu::getRuntimeAvailable)
                .containsOnly(false);
        assertThat(realProjectionService.frontendManifest().getModules()).isEmpty();
    }

    @Test
    void failureAfterRuntimeEnableDisablesRuntimeAndHidesMenus() {
        InMemoryMenuRepo menuRepo = projectedMenus(frontendModule);
        PluginMenuProjectionService projectionService = new PluginMenuProjectionService(menuRepo);
        PluginAppService realProjectionService = appService(projectionService);
        Set<String> runtimeEnabled = statefulRuntimeEnable();
        when(pluginModuleRepo.findAll()).thenReturn(List.of(module));
        when(pluginModuleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pluginRuntimeGateway.permissions(PLUGIN_CODE)).thenThrow(new IllegalStateException("permission failed"));

        assertThatThrownBy(() -> realProjectionService.enable(PLUGIN_CODE))
                .hasMessageContaining("插件启用失败");

        verify(pluginRuntimeGateway).disable(PLUGIN_CODE);
        assertThat(runtimeEnabled).doesNotContain(PLUGIN_CODE);
        assertThat(menuRepo.findByPluginCode(PLUGIN_CODE))
                .extracting(Menu::getRuntimeAvailable)
                .containsOnly(false);
    }

    @Test
    void failedEnableRetriesTransientMenuCleanupUntilAllMenusAreUnavailable() {
        FailOnceMenuRepo menuRepo = new FailOnceMenuRepo();
        PluginMenuProjectionService projectionService = new PluginMenuProjectionService(menuRepo);
        projectionService.project(PLUGIN_CODE, List.of(frontendModule));
        menuRepo.failNextSave();
        PluginAppService realProjectionService = appService(projectionService);
        when(pluginModuleRepo.findAll()).thenReturn(List.of(module));
        when(pluginModuleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("enable failed")).when(pluginRuntimeGateway).enable(module);

        assertThatThrownBy(() -> realProjectionService.enable(PLUGIN_CODE))
                .hasMessageContaining("插件启用失败");

        assertThat(menuRepo.getFailedSaves()).isEqualTo(1);
        assertThat(menuRepo.findByPluginCode(PLUGIN_CODE))
                .extracting(Menu::getRuntimeAvailable)
                .containsOnly(false);
    }

    @Test
    void failedEnablePersistsCleanupFailureInDiagnostics() {
        when(pluginModuleRepo.findAll()).thenReturn(List.of(module));
        when(pluginModuleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("enable failed")).when(pluginRuntimeGateway).enable(module);
        doThrow(new IllegalStateException("mongo unavailable"))
                .when(pluginMenuProjectionService).markUnavailable(PLUGIN_CODE);

        assertThatThrownBy(() -> service.enable(PLUGIN_CODE))
                .hasMessageContaining("插件启用失败");

        assertThat(module.getStatus()).isEqualTo(PluginStatus.ERROR);
        assertThat(module.getErrorMessage()).contains("菜单清理失败", "mongo unavailable");
    }

    @Test
    void restoreFailureDisablesRuntimeAndHidesPreExistingMenus() {
        module.markEnabled();
        InMemoryMenuRepo menuRepo = projectedMenus(frontendModule);
        PluginMenuProjectionService projectionService = new PluginMenuProjectionService(menuRepo);
        PluginAppService realProjectionService = appService(projectionService);
        Set<String> runtimeEnabled = statefulRuntimeEnable();
        when(pluginRuntimeGateway.discover()).thenReturn(List.of());
        when(pluginModuleRepo.findAll()).thenReturn(List.of(module));
        when(pluginModuleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pluginRuntimeGateway.permissions(PLUGIN_CODE)).thenThrow(new IllegalStateException("permission failed"));

        realProjectionService.restoreEnabledPlugins();

        verify(pluginRuntimeGateway).disable(PLUGIN_CODE);
        assertThat(runtimeEnabled).doesNotContain(PLUGIN_CODE);
        assertThat(menuRepo.findByPluginCode(PLUGIN_CODE))
                .extracting(Menu::getRuntimeAvailable)
                .containsOnly(false);
    }

    @Test
    void unprojectedRuntimeModuleIsExcludedFromFrontendManifest() {
        PluginMenuProjectionService projectionService = new PluginMenuProjectionService(new InMemoryMenuRepo());
        module.markEnabled();
        when(pluginRuntimeGateway.frontendModules()).thenReturn(List.of(frontendModule));
        when(pluginRuntimeGateway.enabled(PLUGIN_CODE)).thenReturn(true);
        when(pluginModuleRepo.findAll()).thenReturn(List.of(module));
        PluginAppService realProjectionService = new PluginAppService(
                pluginModuleRepo,
                pluginRuntimeGateway,
                permissionDomainService,
                projectionService
        );

        PluginFrontendManifestDTO manifest = realProjectionService.frontendManifest();

        assertThat(manifest.getModules()).isEmpty();
    }

    @Test
    void runtimeEnabledModuleWithPersistedErrorIsExcludedFromManifest() {
        module.markError("broken state");
        InMemoryMenuRepo menuRepo = projectedMenus(frontendModule);
        when(pluginRuntimeGateway.frontendModules()).thenReturn(List.of(frontendModule));
        when(pluginModuleRepo.findAll()).thenReturn(List.of(module));

        PluginFrontendManifestDTO manifest = appService(new PluginMenuProjectionService(menuRepo)).frontendManifest();

        assertThat(manifest.getModules()).isEmpty();
    }

    @Test
    void explicitEnableRepairsAlreadyRunningModuleStatePermissionsAndProjection() {
        module.markError("stale error");
        when(pluginModuleRepo.findAll()).thenReturn(List.of(module));
        when(pluginModuleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pluginRuntimeGateway.enabled(PLUGIN_CODE)).thenReturn(true);
        when(pluginRuntimeGateway.frontendModules()).thenReturn(List.of(frontendModule));
        when(pluginRuntimeGateway.permissions(PLUGIN_CODE)).thenReturn(List.of());

        service.enable(PLUGIN_CODE);

        assertThat(module.getStatus()).isEqualTo(PluginStatus.ENABLED);
        assertThat(module.getErrorMessage()).isNull();
        verify(permissionDomainService).upsertManualPermissions(List.of());
        verify(pluginMenuProjectionService).project(PLUGIN_CODE, List.of(frontendModule));
        verify(pluginModuleRepo).save(module);
    }

    @Test
    void saveFrontendSortUpdatesProjectedMenusUsedByRefreshedManifest() {
        InMemoryMenuRepo menuRepo = projectedMenus(frontendModule);
        PluginAppService projectionBackedService = sortReadyService(menuRepo);

        projectionBackedService.saveFrontendSort(PLUGIN_CODE, sortCommand());
        PluginFrontendManifestDTO refreshed = projectionBackedService.frontendManifest();

        assertThat(refreshed.getModules().getFirst().getMenuSort()).isEqualTo(101);
        PluginFrontendRouteDTO route = refreshed.getModules().getFirst().getRoutes().getFirst();
        assertThat(route.getSort()).isEqualTo(102);
        assertThat(route.getParentSort()).isEqualTo(103);
        assertThat(projectionBackedService.frontendManifest().getModules().getFirst().getMenuSort()).isEqualTo(101);
    }

    @Test
    void saveFrontendSortRejectsMissingProjectedRouteWithoutPartialUpdates() {
        InMemoryMenuRepo menuRepo = projectedMenus(frontendModule);
        Menu moduleMenu = menuRepo.findByPluginCodeAndRegistrationKey(
                PLUGIN_CODE,
                "module:" + MODULE_NAME
        ).orElseThrow();
        menuRepo.removeByRegistrationKey("route:" + MODULE_NAME + ":walletTransactions");
        PluginAppService projectionBackedService = sortValidationService(menuRepo);

        assertThatThrownBy(() -> projectionBackedService.saveFrontendSort(PLUGIN_CODE, sortCommand()))
                .hasMessageContaining("插件菜单投影不存在");

        assertThat(moduleMenu.getSort()).isEqualTo(20);
    }

    @Test
    void saveFrontendSortRejectsUnavailableProjectedRouteWithoutPartialUpdates() {
        InMemoryMenuRepo menuRepo = projectedMenus(frontendModule);
        Menu moduleMenu = menuRepo.findByPluginCodeAndRegistrationKey(
                PLUGIN_CODE,
                "module:" + MODULE_NAME
        ).orElseThrow();
        Menu routeMenu = menuRepo.findByPluginCodeAndRegistrationKey(
                PLUGIN_CODE,
                "route:" + MODULE_NAME + ":walletTransactions"
        ).orElseThrow();
        routeMenu.setRuntimeAvailable(false);
        menuRepo.save(routeMenu);
        PluginAppService projectionBackedService = sortValidationService(menuRepo);

        assertThatThrownBy(() -> projectionBackedService.saveFrontendSort(PLUGIN_CODE, sortCommand()))
                .hasMessageContaining("插件菜单投影不可用");

        assertThat(moduleMenu.getSort()).isEqualTo(20);
    }

    @Test
    void saveFrontendSortRejectsConflictingDuplicateRouteSortsWithoutPartialUpdates() {
        InMemoryMenuRepo menuRepo = projectedMenus(frontendModule);
        Menu moduleMenu = menuRepo.findByPluginCodeAndRegistrationKey(
                PLUGIN_CODE,
                "module:" + MODULE_NAME
        ).orElseThrow();
        PluginAppService projectionBackedService = sortValidationService(menuRepo);
        PluginFrontendSortSaveCmd command = PluginFrontendSortSaveCmd.builder()
                .moduleName(MODULE_NAME)
                .menuSort(101)
                .routes(List.of(
                        routeSortCommand(102, 103),
                        routeSortCommand(202, 103)
                ))
                .build();

        assertThatThrownBy(() -> projectionBackedService.saveFrontendSort(PLUGIN_CODE, command))
                .hasMessageContaining("插件菜单路由排序冲突");

        assertThat(moduleMenu.getSort()).isEqualTo(20);
    }

    @Test
    void disableMarksPluginMenusUnavailable() {
        stubModuleLookup();

        service.disable(PLUGIN_CODE);

        InOrder order = inOrder(pluginRuntimeGateway, pluginModuleRepo, pluginMenuProjectionService);
        order.verify(pluginRuntimeGateway).disable(PLUGIN_CODE);
        order.verify(pluginModuleRepo).save(module);
        order.verify(pluginMenuProjectionService).markUnavailable(PLUGIN_CODE);
    }

    @Test
    void unloadMarksPluginMenusUnavailable() {
        stubModuleLookup();

        service.unload(PLUGIN_CODE);

        InOrder order = inOrder(pluginRuntimeGateway, pluginModuleRepo, pluginMenuProjectionService);
        order.verify(pluginRuntimeGateway).unload(PLUGIN_CODE);
        order.verify(pluginModuleRepo).save(module);
        order.verify(pluginMenuProjectionService).markUnavailable(PLUGIN_CODE);
    }

    @Test
    void disableRetriesTransientMenuCleanupAfterPersistingDisabledState() {
        FailOnceMenuRepo menuRepo = new FailOnceMenuRepo();
        PluginMenuProjectionService projectionService = new PluginMenuProjectionService(menuRepo);
        projectionService.project(PLUGIN_CODE, List.of(frontendModule));
        menuRepo.failNextSave();
        when(pluginModuleRepo.findByCode(PLUGIN_CODE)).thenReturn(Optional.of(module));
        when(pluginModuleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        appService(projectionService).disable(PLUGIN_CODE);

        assertThat(module.getStatus()).isEqualTo(PluginStatus.DISABLED);
        assertThat(menuRepo.getFailedSaves()).isEqualTo(1);
        assertThat(menuRepo.findByPluginCode(PLUGIN_CODE))
                .extracting(Menu::getRuntimeAvailable)
                .containsOnly(false);
    }

    @Test
    void unloadRetriesTransientMenuCleanupAfterPersistingUnloadedState() {
        FailOnceMenuRepo menuRepo = new FailOnceMenuRepo();
        PluginMenuProjectionService projectionService = new PluginMenuProjectionService(menuRepo);
        projectionService.project(PLUGIN_CODE, List.of(frontendModule));
        menuRepo.failNextSave();
        when(pluginModuleRepo.findByCode(PLUGIN_CODE)).thenReturn(Optional.of(module));
        when(pluginModuleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        appService(projectionService).unload(PLUGIN_CODE);

        assertThat(module.getStatus()).isEqualTo(PluginStatus.INSTALLED);
        assertThat(menuRepo.getFailedSaves()).isEqualTo(1);
        assertThat(menuRepo.findByPluginCode(PLUGIN_CODE))
                .extracting(Menu::getRuntimeAvailable)
                .containsOnly(false);
    }

    @Test
    void listReconcilesMenusForPluginsThatAreNotRuntimeEnabled() {
        module.markDisabled();
        InMemoryMenuRepo menuRepo = projectedMenus(frontendModule);
        when(pluginRuntimeGateway.discover()).thenReturn(List.of());
        when(pluginModuleRepo.findAll()).thenReturn(List.of(module));

        appService(new PluginMenuProjectionService(menuRepo)).list();

        assertThat(menuRepo.findByPluginCode(PLUGIN_CODE))
                .extracting(Menu::getRuntimeAvailable)
                .containsOnly(false);
    }

    @Test
    void listReconcilesOrphanedPluginMenusWithoutAModuleRecord() {
        InMemoryMenuRepo menuRepo = projectedMenus(frontendModule);
        when(pluginRuntimeGateway.discover()).thenReturn(List.of());
        when(pluginModuleRepo.findAll()).thenReturn(List.of());

        appService(new PluginMenuProjectionService(menuRepo)).list();

        assertThat(menuRepo.findByPluginCode(PLUGIN_CODE))
                .extracting(Menu::getRuntimeAvailable)
                .containsOnly(false);
    }

    @Test
    void listSelfHealsZombieRuntimeAfterDisableAndCleanupExhaustion() {
        module.markEnabled();
        FailOnceMenuRepo menuRepo = new FailOnceMenuRepo();
        PluginMenuProjectionService projectionService = new PluginMenuProjectionService(menuRepo);
        projectionService.project(PLUGIN_CODE, List.of(frontendModule));
        menuRepo.failNextSaves(3);
        Set<String> runtimeEnabled = new HashSet<>();
        runtimeEnabled.add(PLUGIN_CODE);
        when(pluginRuntimeGateway.enabled(PLUGIN_CODE))
                .thenAnswer(invocation -> runtimeEnabled.contains(PLUGIN_CODE));
        AtomicInteger disableAttempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (disableAttempts.getAndIncrement() == 0) {
                throw new IllegalStateException("runtime disable failed");
            }
            runtimeEnabled.remove(invocation.<String>getArgument(0));
            return null;
        }).when(pluginRuntimeGateway).disable(PLUGIN_CODE);
        when(pluginModuleRepo.findByCode(PLUGIN_CODE)).thenReturn(Optional.of(module));
        when(pluginModuleRepo.findAll()).thenReturn(List.of(module));
        when(pluginModuleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pluginRuntimeGateway.discover()).thenReturn(List.of());

        assertThatThrownBy(() -> appService(projectionService).disable(PLUGIN_CODE))
                .hasMessageContaining("runtime disable failed");
        assertThat(module.getStatus()).isEqualTo(PluginStatus.DISABLED);
        assertThat(runtimeEnabled).contains(PLUGIN_CODE);
        assertThat(menuRepo.findByPluginCode(PLUGIN_CODE))
                .extracting(Menu::getRuntimeAvailable)
                .containsOnly(true);

        appService(projectionService).list();

        assertThat(runtimeEnabled).doesNotContain(PLUGIN_CODE);
        assertThat(menuRepo.findByPluginCode(PLUGIN_CODE))
                .extracting(Menu::getRuntimeAvailable)
                .containsOnly(false);
    }

    @Test
    void deleteRetainsPluginMenuProjectionRecordsAsUnavailable() {
        when(pluginModuleRepo.findByCode(PLUGIN_CODE)).thenReturn(Optional.of(module));
        when(pluginRuntimeGateway.enabled(PLUGIN_CODE)).thenReturn(true);
        when(pluginRuntimeGateway.loaded(PLUGIN_CODE)).thenReturn(true);

        service.delete(PLUGIN_CODE);

        InOrder order = inOrder(pluginRuntimeGateway, pluginMenuProjectionService, pluginModuleRepo);
        order.verify(pluginRuntimeGateway).disable(PLUGIN_CODE);
        order.verify(pluginRuntimeGateway).unload(PLUGIN_CODE);
        order.verify(pluginMenuProjectionService).markUnavailable(PLUGIN_CODE);
        order.verify(pluginModuleRepo).deleteByCode(PLUGIN_CODE);
    }

    @Test
    void reinstallAppliesPersistedOverridesToFrontendManifest() {
        InMemoryMenuRepo menuRepo = new InMemoryMenuRepo();
        PluginMenuProjectionService projectionService = new PluginMenuProjectionService(menuRepo);
        projectionService.project(PLUGIN_CODE, List.of(frontendModule));
        Menu moduleMenu = menuRepo.findByPluginCodeAndRegistrationKey(
                PLUGIN_CODE,
                "module:" + MODULE_NAME
        ).orElseThrow();
        moduleMenu.setName("自定义钱包中心");
        moduleMenu.setIcon("custom-wallet");
        moduleMenu.setSort(88);
        moduleMenu.setType(MenuNodeType.LAYOUT);
        moduleMenu.setParentCode("system:platform");
        moduleMenu.setModule("custom-wallet-module");
        moduleMenu.setPath("/custom-wallet");
        moduleMenu.setComponent("CustomWalletLayout");
        moduleMenu.setLink("https://wallet.example.test");
        moduleMenu.setPermission("custom:wallet:access");
        moduleMenu.setVisible(false);
        menuRepo.save(moduleMenu);
        Menu parentMenu = menuRepo.findByPluginCodeAndRegistrationKey(
                PLUGIN_CODE,
                "parent:" + MODULE_NAME + ":/wallet"
        ).orElseThrow();
        parentMenu.setName("自定义钱包目录");
        parentMenu.setType(MenuNodeType.LINK);
        parentMenu.setParentCode("system:dashboard");
        parentMenu.setModule("custom-wallet-directory");
        parentMenu.setIcon("custom-directory-icon");
        parentMenu.setPath("/custom-wallet-directory");
        parentMenu.setComponent("CustomDirectoryLayout");
        parentMenu.setLink("https://directory.example.test");
        parentMenu.setPermission("custom:directory:access");
        parentMenu.setSort(89);
        parentMenu.setVisible(false);
        menuRepo.save(parentMenu);
        Menu routeMenu = menuRepo.findByPluginCodeAndRegistrationKey(
                PLUGIN_CODE,
                "route:" + MODULE_NAME + ":walletTransactions"
        ).orElseThrow();
        routeMenu.setName("自定义交易记录");
        routeMenu.setPath("/custom-transactions");
        routeMenu.setComponent("custom/transactions/index");
        routeMenu.setIcon("custom-icon");
        routeMenu.setPermission("custom:transaction:list");
        routeMenu.setType(MenuNodeType.LINK);
        routeMenu.setModule("custom-wallet-page");
        routeMenu.setLink("https://transactions.example.test");
        routeMenu.setSort(91);
        routeMenu.setVisible(false);
        menuRepo.save(routeMenu);
        projectionService.markUnavailable(PLUGIN_CODE);
        projectionService.project(PLUGIN_CODE, List.of(frontendModule));
        module.markEnabled();
        when(pluginRuntimeGateway.frontendModules()).thenReturn(List.of(frontendModule));
        when(pluginRuntimeGateway.enabled(PLUGIN_CODE)).thenReturn(true);
        when(pluginModuleRepo.findAll()).thenReturn(List.of(module));
        PluginAppService realProjectionService = new PluginAppService(
                pluginModuleRepo,
                pluginRuntimeGateway,
                permissionDomainService,
                projectionService
        );

        PluginFrontendManifestDTO manifest = realProjectionService.frontendManifest();

        PluginFrontendModuleDTO frontend = manifest.getModules().getFirst();
        assertThat(frontend.getMenuTitle()).isEqualTo("自定义钱包中心");
        assertThat(frontend.getMenuIcon()).isEqualTo("custom-wallet");
        assertThat(frontend.getMenuSort()).isEqualTo(88);
        assertThat(frontend.getMenuCode()).isEqualTo(moduleMenu.getCode());
        assertThat(frontend.getMenuType()).isEqualTo(MenuNodeType.LAYOUT);
        assertThat(frontend.getParentCode()).isEqualTo("system:platform");
        assertThat(frontend.getMenuModule()).isEqualTo("custom-wallet-module");
        assertThat(frontend.getMenuPath()).isEqualTo("/custom-wallet");
        assertThat(frontend.getMenuComponent()).isEqualTo("CustomWalletLayout");
        assertThat(frontend.getMenuLink()).isEqualTo("https://wallet.example.test");
        assertThat(frontend.getMenuPermission()).isEqualTo("custom:wallet:access");
        assertThat(frontend.getVisible()).isFalse();
        assertThat(frontend.getStatus()).isEqualTo(MenuStatus.ACTIVE);
        PluginFrontendRouteDTO route = manifest.getModules().getFirst().getRoutes().getFirst();
        assertThat(route.getTitle()).isEqualTo("自定义交易记录");
        assertThat(route.getPath()).isEqualTo("/custom-transactions");
        assertThat(route.getComponent()).isEqualTo("custom/transactions/index");
        assertThat(route.getIcon()).isEqualTo("custom-icon");
        assertThat(route.getPermission()).isEqualTo("custom:transaction:list");
        assertThat(route.getMenuCode()).isEqualTo(routeMenu.getCode());
        assertThat(route.getType()).isEqualTo(MenuNodeType.LINK);
        assertThat(route.getModule()).isEqualTo("custom-wallet-page");
        assertThat(route.getLink()).isEqualTo("https://transactions.example.test");
        assertThat(route.getParentCode()).isEqualTo(parentMenu.getCode());
        assertThat(route.getSort()).isEqualTo(91);
        assertThat(route.getVisible()).isFalse();
        assertThat(route.getStatus()).isEqualTo(MenuStatus.ACTIVE);
        assertThat(route.getParentMenuCode()).isEqualTo(parentMenu.getCode());
        assertThat(route.getParentParentCode()).isEqualTo("system:dashboard");
        assertThat(route.getParentTitle()).isEqualTo("自定义钱包目录");
        assertThat(route.getParentType()).isEqualTo(MenuNodeType.LINK);
        assertThat(route.getParentModule()).isEqualTo("custom-wallet-directory");
        assertThat(route.getParentPath()).isEqualTo("/custom-wallet-directory");
        assertThat(route.getParentComponent()).isEqualTo("CustomDirectoryLayout");
        assertThat(route.getParentLink()).isEqualTo("https://directory.example.test");
        assertThat(route.getParentPermission()).isEqualTo("custom:directory:access");
        assertThat(route.getParentIcon()).isEqualTo("custom-directory-icon");
        assertThat(route.getParentSort()).isEqualTo(89);
        assertThat(route.getParentVisible()).isFalse();
        assertThat(route.getParentStatus()).isEqualTo(MenuStatus.ACTIVE);
    }

    @Test
    void disabledMenuIsExcludedAndInvisibleMenuRemainsRoutable() {
        InMemoryMenuRepo menuRepo = new InMemoryMenuRepo();
        PluginMenuProjectionService projectionService = new PluginMenuProjectionService(menuRepo);
        PluginFrontendModuleInfo moduleWithTwoRoutes = frontendModule(List.of(
                route("/wallet/transactions", "walletTransactions"),
                route("/wallet/reports", "walletReports")
        ));
        projectionService.project(PLUGIN_CODE, List.of(moduleWithTwoRoutes));
        module.markEnabled();
        Menu disabled = menuRepo.findByPluginCodeAndRegistrationKey(
                PLUGIN_CODE,
                "route:" + MODULE_NAME + ":walletReports"
        ).orElseThrow();
        disabled.disable();
        menuRepo.save(disabled);
        Menu invisible = menuRepo.findByPluginCodeAndRegistrationKey(
                PLUGIN_CODE,
                "route:" + MODULE_NAME + ":walletTransactions"
        ).orElseThrow();
        invisible.setVisible(false);
        menuRepo.save(invisible);
        when(pluginRuntimeGateway.frontendModules()).thenReturn(List.of(moduleWithTwoRoutes));
        when(pluginRuntimeGateway.enabled(PLUGIN_CODE)).thenReturn(true);
        when(pluginModuleRepo.findAll()).thenReturn(List.of(module));
        PluginAppService realProjectionService = new PluginAppService(
                pluginModuleRepo,
                pluginRuntimeGateway,
                permissionDomainService,
                projectionService
        );

        PluginFrontendManifestDTO manifest = realProjectionService.frontendManifest();

        assertThat(manifest.getModules()).hasSize(1);
        assertThat(manifest.getModules().getFirst().getRoutes())
                .extracting(PluginFrontendRouteDTO::getName)
                .containsExactly("walletTransactions");
        assertThat(manifest.getModules().getFirst().getRoutes().getFirst().getVisible()).isFalse();
    }

    @Test
    void disabledPluginDirectoryExcludesRoutesStillAttachedToIt() {
        InMemoryMenuRepo menuRepo = projectedMenus(frontendModule);
        module.markEnabled();
        PluginMenuProjectionService projectionService = new PluginMenuProjectionService(menuRepo);
        Menu parentMenu = menuRepo.findByPluginCodeAndRegistrationKey(
                PLUGIN_CODE,
                "parent:" + MODULE_NAME + ":/wallet"
        ).orElseThrow();
        parentMenu.setParentCode("system:dashboard");
        parentMenu.disable();
        menuRepo.save(parentMenu);
        when(pluginRuntimeGateway.frontendModules()).thenReturn(List.of(frontendModule));
        when(pluginRuntimeGateway.enabled(PLUGIN_CODE)).thenReturn(true);
        when(pluginModuleRepo.findAll()).thenReturn(List.of(module));

        PluginFrontendManifestDTO manifest = appService(projectionService).frontendManifest();

        assertThat(manifest.getModules()).hasSize(1);
        assertThat(manifest.getModules().getFirst().getRoutes()).isEmpty();
    }

    @Test
    void routeMovedToSystemParentDoesNotExposeStalePluginDirectoryMetadata() {
        InMemoryMenuRepo menuRepo = projectedMenus(frontendModule);
        module.markEnabled();
        Menu parentMenu = menuRepo.findByPluginCodeAndRegistrationKey(
                PLUGIN_CODE,
                "parent:" + MODULE_NAME + ":/wallet"
        ).orElseThrow();
        parentMenu.setParentCode("system:platform");
        parentMenu.setVisible(false);
        menuRepo.save(parentMenu);
        Menu routeMenu = menuRepo.findByPluginCodeAndRegistrationKey(
                PLUGIN_CODE,
                "route:" + MODULE_NAME + ":walletTransactions"
        ).orElseThrow();
        routeMenu.setParentCode("system:dashboard");
        menuRepo.save(routeMenu);
        when(pluginRuntimeGateway.frontendModules()).thenReturn(List.of(frontendModule));
        when(pluginRuntimeGateway.enabled(PLUGIN_CODE)).thenReturn(true);
        when(pluginModuleRepo.findAll()).thenReturn(List.of(module));

        PluginFrontendRouteDTO route = appService(new PluginMenuProjectionService(menuRepo))
                .frontendManifest().getModules().getFirst().getRoutes().getFirst();

        assertThat(route.getParentCode()).isEqualTo("system:dashboard");
        assertThat(route.getParentMenuCode()).isNull();
        assertThat(route.getParentParentCode()).isNull();
        assertThat(route.getParentPath()).isNull();
        assertThat(route.getParentVisible()).isNull();
        assertThat(route.getParentStatus()).isNull();
    }

    private void stubEnable() {
        when(pluginModuleRepo.findAll()).thenReturn(List.of(module));
        when(pluginModuleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pluginRuntimeGateway.frontendModules()).thenReturn(List.of(frontendModule));
        when(pluginRuntimeGateway.permissions(PLUGIN_CODE)).thenReturn(List.of());
    }

    private void stubModuleLookup() {
        when(pluginModuleRepo.findByCode(PLUGIN_CODE)).thenReturn(Optional.of(module));
        when(pluginModuleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private PluginAppService appService(PluginMenuProjectionService projectionService) {
        return new PluginAppService(
                pluginModuleRepo,
                pluginRuntimeGateway,
                permissionDomainService,
                projectionService
        );
    }

    private PluginAppService sortReadyService(InMemoryMenuRepo menuRepo) {
        module.markEnabled();
        when(pluginModuleRepo.findByCode(PLUGIN_CODE)).thenReturn(Optional.of(module));
        when(pluginModuleRepo.findAll()).thenReturn(List.of(module));
        when(pluginModuleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pluginRuntimeGateway.enabled(PLUGIN_CODE)).thenReturn(true);
        when(pluginRuntimeGateway.frontendModules()).thenReturn(List.of(frontendModule));
        return appService(new PluginMenuProjectionService(menuRepo));
    }

    private PluginAppService sortValidationService(InMemoryMenuRepo menuRepo) {
        when(pluginModuleRepo.findByCode(PLUGIN_CODE)).thenReturn(Optional.of(module));
        when(pluginRuntimeGateway.frontendModules()).thenReturn(List.of(frontendModule));
        return appService(new PluginMenuProjectionService(menuRepo));
    }

    private PluginFrontendSortSaveCmd sortCommand() {
        return PluginFrontendSortSaveCmd.builder()
                .moduleName(MODULE_NAME)
                .menuSort(101)
                .routes(List.of(routeSortCommand(102, 103)))
                .build();
    }

    private PluginFrontendRouteSortSaveCmd routeSortCommand(Integer sort, Integer parentSort) {
        return PluginFrontendRouteSortSaveCmd.builder()
                .name("walletTransactions")
                .sort(sort)
                .parentSort(parentSort)
                .build();
    }

    private InMemoryMenuRepo projectedMenus(PluginFrontendModuleInfo declaration) {
        InMemoryMenuRepo menuRepo = new InMemoryMenuRepo();
        new PluginMenuProjectionService(menuRepo).project(PLUGIN_CODE, List.of(declaration));
        return menuRepo;
    }

    private Set<String> statefulRuntimeEnable() {
        Set<String> runtimeEnabled = new HashSet<>();
        doAnswer(invocation -> {
            runtimeEnabled.add(invocation.<PluginModule>getArgument(0).getCode());
            return null;
        }).when(pluginRuntimeGateway).enable(any());
        doAnswer(invocation -> {
            runtimeEnabled.remove(invocation.<String>getArgument(0));
            return null;
        }).when(pluginRuntimeGateway).disable(any());
        when(pluginRuntimeGateway.enabled(any())).thenAnswer(invocation -> runtimeEnabled.contains(invocation.getArgument(0)));
        return runtimeEnabled;
    }

    private PluginFrontendModuleInfo frontendModule() {
        return frontendModule(List.of(route("/wallet/transactions", "walletTransactions")));
    }

    private PluginFrontendModuleInfo frontendModule(List<PluginFrontendRouteInfo> routes) {
        return new PluginFrontendModuleInfo(
                PLUGIN_CODE,
                "/api/platform/plugins/yudream-wallet/assets/remoteEntry.js",
                MODULE_NAME,
                "1.0.0",
                "sha256-test",
                "钱包中心",
                "wallet",
                20,
                routes
        );
    }

    private PluginFrontendRouteInfo route(String path, String name) {
        return new PluginFrontendRouteInfo(
                path,
                name,
                name,
                "list",
                "/wallet",
                "钱包管理",
                "folder",
                10,
                "wallet/transactions/index",
                "wallet:transaction:list",
                30
        );
    }

    private static class InMemoryMenuRepo implements MenuRepo {

        private final Map<String, Menu> menus = new LinkedHashMap<>();

        @Override
        public Menu save(Menu menu) {
            menus.put(menu.getCode(), menu);
            return menu;
        }

        @Override
        public Optional<Menu> findByCode(String code) {
            return Optional.ofNullable(menus.get(code));
        }

        @Override
        public Optional<Menu> findByPluginCodeAndRegistrationKey(String pluginCode, String registrationKey) {
            return findByPluginCode(pluginCode).stream()
                    .filter(menu -> registrationKey.equals(menu.getPluginRegistrationKey()))
                    .findFirst();
        }

        @Override
        public List<Menu> findAll() {
            return List.copyOf(menus.values());
        }

        @Override
        public List<Menu> findByPluginCode(String pluginCode) {
            return menus.values().stream()
                    .filter(Menu::isPluginMenu)
                    .filter(menu -> pluginCode.equals(menu.getPluginCode()))
                    .toList();
        }

        @Override
        public List<Menu> findByTypeIn(List<MenuNodeType> types) {
            return menus.values().stream().filter(menu -> types.contains(menu.getType())).toList();
        }

        @Override
        public boolean existsByCode(String code) {
            return menus.containsKey(code);
        }

        @Override
        public long count() {
            return menus.size();
        }

        void removeByRegistrationKey(String registrationKey) {
            menus.values().removeIf(menu -> registrationKey.equals(menu.getPluginRegistrationKey()));
        }
    }

    private static class FailOnceMenuRepo extends InMemoryMenuRepo {

        private int remainingFailures;
        private int failedSaves;

        void failNextSave() {
            failNextSaves(1);
        }

        void failNextSaves(int count) {
            remainingFailures = count;
        }

        int getFailedSaves() {
            return failedSaves;
        }

        @Override
        public Menu save(Menu menu) {
            if (remainingFailures > 0) {
                remainingFailures--;
                failedSaves++;
                menu.setRuntimeAvailable(true);
                throw new IllegalStateException("transient mongo failure");
            }
            return super.save(menu);
        }
    }

    private static class FailingProjectionService extends PluginMenuProjectionService {

        private final String failingPluginCode;
        private boolean fail = true;

        FailingProjectionService(MenuRepo menuRepo, String failingPluginCode) {
            super(menuRepo);
            this.failingPluginCode = failingPluginCode;
        }

        @Override
        public void project(String pluginCode, List<PluginFrontendModuleInfo> modules) {
            super.project(pluginCode, modules);
            if (fail && failingPluginCode.equals(pluginCode)) {
                fail = false;
                throw new IllegalStateException("projection failed");
            }
        }
    }
}
