package online.yudream.base.application.platform.plugin;

import online.yudream.base.application.platform.plugin.service.PluginAppService;
import online.yudream.base.application.platform.plugin.service.PluginStoreAppService;
import online.yudream.base.application.platform.plugin.dto.PluginModuleDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.port.PluginStoreGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginCompatibility;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDependency;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDescriptor;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDetail;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginJar;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginStoreAppServiceTest {

    @Mock
    private PluginStoreGateway pluginStoreGateway;

    @Mock
    private PluginAppService pluginAppService;

    @Test
    void listsPluginsThroughStoreGateway() {
        PluginStorePluginInfo plugin = new PluginStorePluginInfo();
        plugin.setCode("demo");
        plugin.setDescriptor(new PluginStorePluginDescriptor(
                "1.0.0", "demo", "1.0.0", "example.Plugin", "Demo", "Plugin description",
                "https://store.example.test/icon.svg", List.of("https://store.example.test/screenshot.png"), null, List.of(),
                new PluginStorePluginJar("example:demo:1.0.0", "https://store.example.test/demo.jar", "a".repeat(64))));
        when(pluginStoreGateway.list()).thenReturn(List.of(plugin));

        var result = new PluginStoreAppService(pluginStoreGateway, pluginAppService).list();

        assertEquals(List.of("demo"), result.stream().map(item -> item.getCode()).toList());
        verify(pluginStoreGateway).list();
    }

    @Test
    void trimsValidCodeBeforeLoadingDetail() {
        PluginStorePluginDetail detail = new PluginStorePluginDetail("demo", List.of());
        when(pluginStoreGateway.detail("demo")).thenReturn(Optional.of(detail));

        var result = new PluginStoreAppService(pluginStoreGateway, pluginAppService).detail(" demo ");

        assertEquals("demo", result.getCode());
        assertEquals(List.of(), result.getVersions());
        verify(pluginStoreGateway).detail("demo");
    }

    @Test
    void rejectsInvalidCodeWithoutCallingGateway() {
        PluginStoreAppService service = new PluginStoreAppService(pluginStoreGateway, pluginAppService);

        assertThrows(BizException.class, () -> service.detail("../demo"));

        verifyNoInteractions(pluginStoreGateway);
    }

    @Test
    void convertsMissingDetailToBusinessError() {
        when(pluginStoreGateway.detail("demo")).thenReturn(Optional.empty());

        assertThrows(BizException.class, () -> new PluginStoreAppService(pluginStoreGateway, pluginAppService).detail("demo"));

        verify(pluginStoreGateway).detail("demo");
    }

    @Test
    void detailMarksIncompatibleAndRequiredDependencyVersionsAsNotInstallable() {
        PluginStorePluginDescriptor incompatible = descriptor("1.0.0",
                new PluginStorePluginCompatibility("^2.0.0", "^2.6.0", "^1.0.0"), List.of());
        PluginStorePluginDescriptor missingRequired = descriptor("2.0.0", null,
                List.of(new PluginStorePluginDependency("base", "^1.2.0", true)));
        when(pluginStoreGateway.detail("demo")).thenReturn(Optional.of(new PluginStorePluginDetail("demo", List.of(
                new PluginStorePluginVersion("1.0.0", incompatible),
                new PluginStorePluginVersion("2.0.0", missingRequired)))));
        when(pluginAppService.list()).thenReturn(List.of());

        var result = new PluginStoreAppService(pluginStoreGateway, pluginAppService).detail("demo");

        assertEquals(false, result.getVersions().get(0).isInstallable());
        assertEquals("宿主版本不满足兼容性要求", result.getVersions().get(0).getInstallDisabledReason());
        assertEquals(false, result.getVersions().get(1).isInstallable());
        assertEquals("必需依赖 base 不可用", result.getVersions().get(1).getInstallDisabledReason());
        verify(pluginAppService).list();
    }

    @Test
    void detailKeepsVersionInstallableWhenOptionalDependencyIsUnavailable() {
        PluginStorePluginDescriptor descriptor = descriptor("1.0.0", null,
                List.of(new PluginStorePluginDependency("optional", "^9.0.0", false)));
        when(pluginStoreGateway.detail("demo")).thenReturn(Optional.of(new PluginStorePluginDetail("demo",
                List.of(new PluginStorePluginVersion("1.0.0", descriptor)))));
        when(pluginAppService.list()).thenReturn(List.of());

        var result = new PluginStoreAppService(pluginStoreGateway, pluginAppService).detail("demo");

        assertEquals(true, result.getVersions().getFirst().isInstallable());
        assertEquals(null, result.getVersions().getFirst().getInstallDisabledReason());
        verify(pluginAppService).list();
    }

    @Test
    void installsSpecifiedStoreVersionWithoutEnablingIt() {
        PluginStorePluginDescriptor descriptor = descriptor("1.0.0");
        when(pluginStoreGateway.detail("demo")).thenReturn(Optional.of(new PluginStorePluginDetail("demo",
                List.of(new PluginStorePluginVersion("1.0.0", descriptor)))));
        PluginStoreAppService service = new PluginStoreAppService(pluginStoreGateway, pluginAppService);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "uploadDirectory",
                System.getProperty("java.io.tmpdir"));

        service.install(" demo ", " 1.0.0 ");

        verify(pluginStoreGateway).downloadJar(eq(descriptor), any());
        verify(pluginAppService).installStoreJar(any(), eq("demo"), eq("1.0.0"), eq("example.Plugin"));
        verifyNoMoreInteractions(pluginAppService);
    }

    @Test
    void rejectsMissingStoreVersionWithoutDownloadingOrInstalling() {
        when(pluginStoreGateway.detail("demo")).thenReturn(Optional.of(new PluginStorePluginDetail("demo", List.of())));

        assertThrows(BizException.class, () -> new PluginStoreAppService(pluginStoreGateway, pluginAppService).install("demo", "1.0.0"));

        verifyNoInteractions(pluginAppService);
    }

    @Test
    void rejectsIncompatibleDescriptorsBeforeDownloading() {
        for (PluginStorePluginCompatibility compatibility : List.of(
                new PluginStorePluginCompatibility("^2.0.0", null, null),
                new PluginStorePluginCompatibility(null, "^3.0.0", null),
                new PluginStorePluginCompatibility(null, null, "^2.0.0"))) {
            PluginStorePluginDescriptor descriptor = descriptor("1.0.0", compatibility, List.of());
            when(pluginStoreGateway.detail("demo")).thenReturn(Optional.of(new PluginStorePluginDetail("demo",
                    List.of(new PluginStorePluginVersion("1.0.0", descriptor)))));

            assertThrows(BizException.class, () -> new PluginStoreAppService(pluginStoreGateway, pluginAppService).install("demo", "1.0.0"));
        }

        verifyNoInteractions(pluginAppService);
        verify(pluginStoreGateway, org.mockito.Mockito.never()).downloadJar(any(), any());
    }

    @Test
    void validatesRequiredLocalDependencyWithoutInstallingOptionalDependencies() {
        PluginStorePluginDescriptor descriptor = descriptor("1.0.0", new PluginStorePluginCompatibility("^1.0.0", "^2.6.0", "^1.0.0"),
                List.of(new PluginStorePluginDependency("base", "^1.2.0", true),
                        new PluginStorePluginDependency("optional", "^9.0.0", false)));
        when(pluginStoreGateway.detail("demo")).thenReturn(Optional.of(new PluginStorePluginDetail("demo",
                List.of(new PluginStorePluginVersion("1.0.0", descriptor)))));
        when(pluginAppService.list()).thenReturn(List.of(PluginModuleDTO.builder().code("base").version("1.3.0").build()));
        PluginStoreAppService service = new PluginStoreAppService(pluginStoreGateway, pluginAppService);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "uploadDirectory", System.getProperty("java.io.tmpdir"));

        service.install("demo", "1.0.0");

        verify(pluginAppService).list();
        verify(pluginStoreGateway).downloadJar(eq(descriptor), any());
        verify(pluginAppService).installStoreJar(any(), eq("demo"), eq("1.0.0"), eq("example.Plugin"));
        verifyNoMoreInteractions(pluginAppService);
    }

    @Test
    void rejectsMissingOrIncompatibleRequiredLocalDependencyBeforeDownloading() {
        PluginStorePluginDescriptor descriptor = descriptor("1.0.0", null,
                List.of(new PluginStorePluginDependency("base", "^1.2.0", true)));
        when(pluginStoreGateway.detail("demo")).thenReturn(Optional.of(new PluginStorePluginDetail("demo",
                List.of(new PluginStorePluginVersion("1.0.0", descriptor)))));
        when(pluginAppService.list()).thenReturn(List.of(PluginModuleDTO.builder().code("base").version("2.0.0").build()));

        assertThrows(BizException.class, () -> new PluginStoreAppService(pluginStoreGateway, pluginAppService).install("demo", "1.0.0"));

        verify(pluginAppService).list();
        verify(pluginStoreGateway, org.mockito.Mockito.never()).downloadJar(any(), any());
    }

    @Test
    void checksInstalledPluginUpdatesUsingLatestSemanticVersionAndInstallability() {
        PluginStorePluginDescriptor latest = descriptor("2.0.0", new PluginStorePluginCompatibility("^2.0.0", null, null), List.of());
        when(pluginAppService.listInstalled()).thenReturn(List.of(
                PluginModuleDTO.builder().code("demo").version("1.0.0").build(),
                PluginModuleDTO.builder().code("missing").version("1.0.0").build(),
                PluginModuleDTO.builder().code("invalid").version("not-a-version").build()));
        when(pluginStoreGateway.detail("demo")).thenReturn(Optional.of(new PluginStorePluginDetail("demo", List.of(
                new PluginStorePluginVersion("1.9.0", descriptor("1.9.0")),
                new PluginStorePluginVersion("2.0.0", latest),
                new PluginStorePluginVersion("invalid", descriptor("invalid"))))));
        when(pluginStoreGateway.detail("missing")).thenReturn(Optional.empty());
        when(pluginStoreGateway.detail("invalid")).thenReturn(Optional.of(new PluginStorePluginDetail("invalid", List.of(
                new PluginStorePluginVersion("2.0.0", descriptor("2.0.0"))))));

        var result = new PluginStoreAppService(pluginStoreGateway, pluginAppService).updates();

        assertEquals(2, result.size());
        assertEquals("demo", result.getFirst().getCode());
        assertEquals("2.0.0", result.getFirst().getLatestReleaseVersion());
        assertEquals(true, result.getFirst().isUpdateAvailable());
        assertEquals(false, result.getFirst().isCompatible());
        assertEquals("宿主版本不满足兼容性要求", result.getFirst().getBlockedReason());
        assertEquals("invalid", result.get(1).getCode());
        assertEquals(false, result.get(1).isUpdateAvailable());
        verify(pluginAppService).listInstalled();
        verify(pluginStoreGateway).detail("demo");
        verify(pluginStoreGateway).detail("missing");
        verify(pluginStoreGateway).detail("invalid");
        verify(pluginStoreGateway, org.mockito.Mockito.never()).downloadJar(any(), any());
    }

    @Test
    void buildsReadOnlyUpdatePlanFromInstalledPluginsAndExactTarget() {
        PluginStorePluginDescriptor target = descriptor("2.1.0", null, List.of(
                new PluginStorePluginDependency("base", "^1.2.0", true),
                new PluginStorePluginDependency("optional", "^9.0.0", false)));
        when(pluginAppService.listInstalled()).thenReturn(List.of(
                PluginModuleDTO.builder().code("demo").version("1.0.0").build(),
                PluginModuleDTO.builder().code("base").version("1.3.0").build(),
                PluginModuleDTO.builder().code("hard-client").version("1.0.0")
                        .dependencies(List.of("demo"))
                        .status(online.yudream.base.domain.platform.plugin.enumerate.PluginStatus.ENABLED).build(),
                PluginModuleDTO.builder().code("soft-client").version("1.0.0")
                        .softDependencies(List.of("demo"))
                        .status(online.yudream.base.domain.platform.plugin.enumerate.PluginStatus.ENABLED).build()));
        when(pluginStoreGateway.detail("demo")).thenReturn(Optional.of(new PluginStorePluginDetail("demo", List.of(
                new PluginStorePluginVersion("3.0.0", descriptor("3.0.0")),
                new PluginStorePluginVersion("2.1.0", target),
                new PluginStorePluginVersion("invalid", descriptor("invalid"))))));

        var result = new PluginStoreAppService(pluginStoreGateway, pluginAppService).updatePlan("demo", " 2.1.0 ");

        assertEquals("MAJOR", result.getChangeType());
        assertEquals("2.1.0", result.getToVersion());
        assertEquals(List.of("base"), result.getRequiredDependencies().stream().map(item -> item.getCode()).toList());
        assertEquals(List.of("optional"), result.getOptionalDependencies().stream().map(item -> item.getCode()).toList());
        assertEquals(List.of("hard-client", "soft-client"), result.getAffectedEnabledPlugins());
        assertEquals(true, result.isRequiresRestart());
        assertEquals(null, result.getBlockedReason());
        assertEquals(List.of("可选依赖 optional 不可用"), result.getWarnings());
        verify(pluginAppService).listInstalled();
        verify(pluginStoreGateway).detail("demo");
        verify(pluginAppService, org.mockito.Mockito.never()).list();
        verify(pluginAppService, org.mockito.Mockito.never()).installStoreJar(any(), any(), any(), any());
        verify(pluginStoreGateway, org.mockito.Mockito.never()).downloadJar(any(), any());
    }

    @Test
    void buildsDefaultPlansUsingHighestParsableVersionWithoutSideEffects() {
        when(pluginAppService.listInstalled()).thenReturn(List.of(
                PluginModuleDTO.builder().code("demo").version("1.0.0").build(),
                PluginModuleDTO.builder().code("missing").version("1.0.0").build()));
        when(pluginStoreGateway.detail("demo")).thenReturn(Optional.of(new PluginStorePluginDetail("demo", List.of(
                new PluginStorePluginVersion("1.2.0", descriptor("1.2.0")),
                new PluginStorePluginVersion("2.0.0", descriptor("2.0.0")),
                new PluginStorePluginVersion("invalid", descriptor("invalid"))))));
        when(pluginStoreGateway.detail("missing")).thenReturn(Optional.empty());

        var result = new PluginStoreAppService(pluginStoreGateway, pluginAppService).updatePlans();

        assertEquals(1, result.size());
        assertEquals("demo", result.getFirst().getCode());
        assertEquals("2.0.0", result.getFirst().getToVersion());
        assertEquals("MAJOR", result.getFirst().getChangeType());
        verify(pluginAppService).listInstalled();
        verify(pluginStoreGateway).detail("demo");
        verify(pluginStoreGateway).detail("missing");
        verify(pluginAppService, org.mockito.Mockito.never()).list();
        verify(pluginAppService, org.mockito.Mockito.never()).installStoreJar(any(), any(), any(), any());
        verify(pluginStoreGateway, org.mockito.Mockito.never()).downloadJar(any(), any());
    }

    @Test
    void rejectsBlockedUpdateBeforeDownloading() {
        PluginStorePluginDescriptor target = descriptor("2.0.0", null,
                List.of(new PluginStorePluginDependency("base", "^1.0.0", true)));
        when(pluginAppService.listInstalled()).thenReturn(List.of(PluginModuleDTO.builder().code("demo").version("1.0.0").build()));
        when(pluginStoreGateway.detail("demo")).thenReturn(Optional.of(new PluginStorePluginDetail("demo", List.of(
                new PluginStorePluginVersion("2.0.0", target)))));

        assertThrows(BizException.class, () -> new PluginStoreAppService(pluginStoreGateway, pluginAppService).update("demo", "2.0.0"));

        verify(pluginStoreGateway, org.mockito.Mockito.never()).downloadJar(any(), any());
        verify(pluginAppService, org.mockito.Mockito.never()).updateStoreJar(any(), any(), any(), any());
    }

    @Test
    void updatesRunningPluginThroughControlledUpdateFlowWithoutHotReenable() {
        PluginStorePluginDescriptor target = descriptor("2.0.0");
        when(pluginAppService.listInstalled()).thenReturn(List.of(PluginModuleDTO.builder().code("demo").version("1.0.0").loaded(true).build()));
        when(pluginStoreGateway.detail("demo")).thenReturn(Optional.of(new PluginStorePluginDetail("demo", List.of(
                new PluginStorePluginVersion("2.0.0", target)))));
        PluginStoreAppService service = new PluginStoreAppService(pluginStoreGateway, pluginAppService);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "uploadDirectory", System.getProperty("java.io.tmpdir"));

        var result = service.update("demo", "2.0.0");

        assertEquals(true, result.isRequiresRestart());
        verify(pluginStoreGateway).detail("demo");
        verify(pluginStoreGateway).downloadJar(eq(target), any());
        verify(pluginAppService).updateStoreJar(any(), eq("demo"), eq("2.0.0"), eq("example.Plugin"));
        verify(pluginAppService, org.mockito.Mockito.never()).enable(any());
        verify(pluginAppService, org.mockito.Mockito.never()).disable(any());
        verify(pluginAppService, org.mockito.Mockito.never()).unload(any());
    }

    @Test
    void excludesNonUpgradePlansAndRejectsTheirExecution() {
        when(pluginAppService.listInstalled()).thenReturn(List.of(PluginModuleDTO.builder().code("demo").version("2.0.0").build()));
        when(pluginStoreGateway.detail("demo")).thenReturn(Optional.of(new PluginStorePluginDetail("demo", List.of(
                new PluginStorePluginVersion("2.0.0", descriptor("2.0.0"))))));
        PluginStoreAppService service = new PluginStoreAppService(pluginStoreGateway, pluginAppService);

        assertEquals(List.of(), service.updatePlans());
        assertThrows(BizException.class, () -> service.update("demo", "2.0.0"));
        verify(pluginStoreGateway, org.mockito.Mockito.never()).downloadJar(any(), any());
    }

    @Test
    void rollsBackUsingOnlyLocalBackupWithoutMarketplaceAccessOrEnabling() {
        when(pluginAppService.listInstalled()).thenReturn(List.of(
                PluginModuleDTO.builder().code("demo").version("2.0.0").build()));
        when(pluginAppService.rollbackStoreJar("demo")).thenReturn(List.of(
                PluginModuleDTO.builder().code("demo").version("1.0.0").build()));

        var result = new PluginStoreAppService(pluginStoreGateway, pluginAppService).rollback("demo");

        assertEquals(true, result.isRequiresRestart());
        verify(pluginAppService).rollbackStoreJar("demo");
        verifyNoInteractions(pluginStoreGateway);
        verify(pluginAppService, org.mockito.Mockito.never()).enable(any());
    }

    private PluginStorePluginDescriptor descriptor(String version) {
        return descriptor(version, null, List.of());
    }

    private PluginStorePluginDescriptor descriptor(String version, PluginStorePluginCompatibility compatibility,
                                                   List<PluginStorePluginDependency> dependencies) {
        return new PluginStorePluginDescriptor(version, "demo", version, "example.Plugin", "Demo", null,
                null, List.of(), compatibility, dependencies, new PluginStorePluginJar("example:demo:" + version,
                "https://store.example.test/demo.jar", "a".repeat(64)));
    }
}
