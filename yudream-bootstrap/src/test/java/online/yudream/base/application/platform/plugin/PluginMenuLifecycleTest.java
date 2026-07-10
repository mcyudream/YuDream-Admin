package online.yudream.base.application.platform.plugin;

import online.yudream.base.application.platform.plugin.dto.PluginFrontendManifestDTO;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendModuleDTO;
import online.yudream.base.application.platform.plugin.service.PluginAppService;
import online.yudream.base.application.platform.plugin.service.PluginMenuProjectionService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.enumerate.PluginStatus;
import online.yudream.base.domain.platform.plugin.repo.PluginModuleRepo;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendModuleInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendRouteInfo;
import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.domain.system.menu.enumerate.SeedSyncMode;
import online.yudream.base.domain.system.menu.repo.MenuRepo;
import online.yudream.base.domain.system.user.service.PermissionDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
import static org.mockito.Mockito.times;
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
        order.verify(pluginMenuProjectionService).restoreAvailable(PLUGIN_CODE);
        order.verify(pluginMenuProjectionService).project(PLUGIN_CODE, List.of(frontendModule));
    }

    @Test
    void nonMissingOnlyModeRestoresExistingMenusWithoutRegeneratingDeletedOnes() {
        stubEnable();
        module.markMenusInitialized();
        ReflectionTestUtils.setField(service, "menuSeedSyncMode", SeedSyncMode.INIT_EMPTY);

        service.enable(PLUGIN_CODE);

        verify(pluginMenuProjectionService).restoreAvailable(PLUGIN_CODE);
        verify(pluginMenuProjectionService, never()).project(any(), any());
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
    void healthyRuntimeModuleIsIncludedWithoutMenuProjection() {
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

        assertThat(manifest.getModules()).singleElement()
                .satisfies(frontend -> {
                    assertThat(frontend.getPluginCode()).isEqualTo(PLUGIN_CODE);
                    assertThat(frontend.getModuleName()).isEqualTo(MODULE_NAME);
                    assertThat(frontend.getEntry()).isEqualTo(frontendModule.entry());
                });
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
        verify(pluginModuleRepo, times(2)).save(module);
        assertThat(module.menusInitialized()).isTrue();
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
    void disableReportsFailureWhenMenusCannotBeHidden() {
        FailOnceMenuRepo menuRepo = new FailOnceMenuRepo();
        PluginMenuProjectionService projectionService = new PluginMenuProjectionService(menuRepo);
        projectionService.project(PLUGIN_CODE, List.of(frontendModule));
        menuRepo.failNextSaves(3);
        stubModuleLookup();

        assertThatThrownBy(() -> appService(projectionService).disable(PLUGIN_CODE))
                .hasMessageContaining("菜单清理失败");

        assertThat(module.getStatus()).isEqualTo(PluginStatus.DISABLED);
        assertThat(menuRepo.findByPluginCode(PLUGIN_CODE))
                .extracting(Menu::getRuntimeAvailable)
                .containsOnly(true);
    }

    @Test
    void unloadReportsFailureWhenMenusCannotBeHidden() {
        FailOnceMenuRepo menuRepo = new FailOnceMenuRepo();
        PluginMenuProjectionService projectionService = new PluginMenuProjectionService(menuRepo);
        projectionService.project(PLUGIN_CODE, List.of(frontendModule));
        menuRepo.failNextSaves(3);
        stubModuleLookup();

        assertThatThrownBy(() -> appService(projectionService).unload(PLUGIN_CODE))
                .hasMessageContaining("菜单清理失败");

        assertThat(module.getStatus()).isEqualTo(PluginStatus.INSTALLED);
        assertThat(menuRepo.findByPluginCode(PLUGIN_CODE))
                .extracting(Menu::getRuntimeAvailable)
                .containsOnly(true);
    }

    @Test
    void listDoesNotChangeMenusForPluginsThatAreNotRuntimeEnabled() {
        module.markDisabled();
        InMemoryMenuRepo menuRepo = projectedMenus(frontendModule);
        when(pluginRuntimeGateway.discover()).thenReturn(List.of());
        when(pluginModuleRepo.findAll()).thenReturn(List.of(module));

        appService(new PluginMenuProjectionService(menuRepo)).list();

        assertThat(menuRepo.findByPluginCode(PLUGIN_CODE))
                .extracting(Menu::getRuntimeAvailable)
                .containsOnly(true);
    }

    @Test
    void listDoesNotChangeOrphanedPluginMenus() {
        InMemoryMenuRepo menuRepo = projectedMenus(frontendModule);
        when(pluginRuntimeGateway.discover()).thenReturn(List.of());
        when(pluginModuleRepo.findAll()).thenReturn(List.of());

        appService(new PluginMenuProjectionService(menuRepo)).list();

        assertThat(menuRepo.findByPluginCode(PLUGIN_CODE))
                .extracting(Menu::getRuntimeAvailable)
                .containsOnly(true);
    }

    @Test
    void listSelfHealsZombieRuntimeWithoutMutatingMenuRecords() {
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
                .containsOnly(true);
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
    void deleteStopsBeforeRemovingJarOrModuleWhenMenuCleanupExhaustsRetries() {
        module.markEnabled();
        FailOnceMenuRepo menuRepo = new FailOnceMenuRepo();
        PluginMenuProjectionService projectionService = new PluginMenuProjectionService(menuRepo);
        projectionService.project(PLUGIN_CODE, List.of(frontendModule));
        menuRepo.failNextSaves(3);
        when(pluginModuleRepo.findByCode(PLUGIN_CODE)).thenReturn(Optional.of(module));
        when(pluginModuleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pluginRuntimeGateway.enabled(PLUGIN_CODE)).thenReturn(true);
        when(pluginRuntimeGateway.loaded(PLUGIN_CODE)).thenReturn(true);

        assertThatThrownBy(() -> appService(projectionService).delete(PLUGIN_CODE))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("菜单清理失败");

        assertThat(Path.of(module.getJarPath())).exists();
        assertThat(menuRepo.findByPluginCode(PLUGIN_CODE))
                .extracting(Menu::getRuntimeAvailable)
                .containsOnly(true);
        assertThat(module.getStatus()).isEqualTo(PluginStatus.INSTALLED);
        verify(pluginModuleRepo).save(module);
        verify(pluginModuleRepo, never()).deleteByCode(PLUGIN_CODE);
        assertThat(pluginModuleRepo.findByCode(PLUGIN_CODE)).contains(module);
    }

    @Test
    void reinstallRestoresPersistedMenusWithoutCopyingThemIntoManifest() {
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
        projectionService.restoreAvailable(PLUGIN_CODE);
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
        assertThat(frontend.getMenuTitle()).isNull();
        assertThat(frontend.getMenuCode()).isNull();
        assertThat(frontend.getRoutes()).isEmpty();
        assertThat(moduleMenu.getName()).isEqualTo("自定义钱包中心");
        assertThat(parentMenu.getParentCode()).isEqualTo("system:dashboard");
        assertThat(routeMenu.getComponent()).isEqualTo("custom/transactions/index");
        assertThat(moduleMenu.getRuntimeAvailable()).isTrue();
        assertThat(parentMenu.getRuntimeAvailable()).isTrue();
        assertThat(routeMenu.getRuntimeAvailable()).isTrue();
    }

    @Test
    void persistedMenuStateDoesNotCullRuntimeManifestRoutes() {
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
        assertThat(manifest.getModules().getFirst().getRoutes()).isEmpty();
    }

    @Test
    void disabledPluginDirectoryDoesNotCullRuntimeManifestRoutes() {
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
    void declaredCrossPluginParentDoesNotCreateConsumerModuleDirectory() {
        String studentInfoModuleCode = "plugin:yudream-student-info:module:yudreamStudentInfo";
        PluginFrontendModuleInfo activityProofModule = new PluginFrontendModuleInfo(
                "minecraft-activity-proof",
                "/api/platform/plugins/minecraft-activity-proof/assets/remoteEntry.js",
                "minecraftActivityProof",
                "1.0.0",
                "sha256-test",
                "学生信息",
                "i-ri:id-card-line",
                35,
                List.of(),
                studentInfoModuleCode,
                true,
                MenuStatus.ACTIVE
        );
        InMemoryMenuRepo menuRepo = new InMemoryMenuRepo();

        new PluginMenuProjectionService(menuRepo).project("minecraft-activity-proof", List.of(activityProofModule));

        assertThat(menuRepo.findByPluginCodeAndRegistrationKey(
                "minecraft-activity-proof",
                "module:minecraftActivityProof"
        )).isEmpty();
    }

    @Test
    void routeMovedToSystemParentIsNotReflectedBackIntoRuntimeManifest() {
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

        PluginFrontendModuleDTO runtimeModule = appService(new PluginMenuProjectionService(menuRepo))
                .frontendManifest().getModules().getFirst();

        assertThat(runtimeModule.getRoutes()).isEmpty();
        assertThat(routeMenu.getParentCode()).isEqualTo("system:dashboard");
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
