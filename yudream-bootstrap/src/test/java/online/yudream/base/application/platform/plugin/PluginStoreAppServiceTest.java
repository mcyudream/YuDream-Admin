package online.yudream.base.application.platform.plugin;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.plugin.service.PluginAppService;
import online.yudream.base.application.platform.plugin.service.PluginMarketSourceAppService;
import online.yudream.base.application.platform.plugin.service.PluginStoreAppService;
import online.yudream.base.application.platform.plugin.dto.PluginModuleDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.aggregate.PluginMarketSource;
import online.yudream.base.domain.platform.plugin.port.PluginStoreGateway;
import online.yudream.base.domain.platform.plugin.repo.PluginMarketSourceRepo;
import online.yudream.base.domain.platform.plugin.repo.PluginMarketSourceSnapshotRepo;
import online.yudream.base.domain.platform.plugin.valobj.PluginStoreCatalogEntry;
import online.yudream.base.domain.platform.plugin.valobj.PluginStoreCatalogVersion;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginCompatibility;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDependency;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDescriptor;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginJar;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginVersion;
import online.yudream.base.domain.platform.plugin.valobj.PluginStoreSourceRef;
import online.yudream.base.domain.platform.plugin.valobj.PluginStoreStructuredVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginStoreAppServiceTest {

    private static final String STORE_ROOT = "https://store.example.test/index.json";

    @Mock
    private PluginStoreGateway pluginStoreGateway;

    @Mock
    private PluginAppService pluginAppService;

    @Mock
    private PluginMarketSourceAppService pluginMarketSourceAppService;

    @Mock
    private PluginMarketSourceRepo pluginMarketSourceRepo;

    @Mock
    private PluginMarketSourceSnapshotRepo pluginMarketSourceSnapshotRepo;

    @Mock
    private CapabilityAppService capabilityAppService;

    private PluginStoreAppService service() {
        return new PluginStoreAppService(pluginStoreGateway, pluginAppService, pluginMarketSourceAppService);
    }

    private PluginStoreAppService serviceWithUploadDirectory() {
        PluginStoreAppService service = service();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "uploadDirectory",
                System.getProperty("java.io.tmpdir"));
        return service;
    }

    /** 能力开启后的单源目录（取代已删除的 Nexus 隐式回落）。 */
    private void stubLegacyCatalog(PluginStoreCatalogEntry... entries) {
        PluginMarketSource source = source("default", "本机插件市场", STORE_ROOT);
        stubMultiSource(new PluginMarketSourceAppService.SourceCatalog(source, snapshot(source, entries)));
    }

    private void stubMultiSource(PluginMarketSourceAppService.SourceCatalog... catalogs) {
        when(pluginMarketSourceAppService.enabledSourceCatalogs()).thenReturn(List.of(catalogs));
        for (PluginMarketSourceAppService.SourceCatalog catalog : catalogs) {
            // 未被解析到的源不会触发 sourceRef，按宽松桩处理
            org.mockito.Mockito.lenient().when(pluginMarketSourceAppService.sourceRef(catalog.source()))
                    .thenReturn(new PluginStoreSourceRef(catalog.source().getRootUrl(), null));
        }
    }

    /** entry 的最新版本为 releaseVersions 的最后一个；其 descriptor 通过 parseDescriptor 解析。 */
    private PluginStoreCatalogEntry entry(String code, String... releaseVersions) {
        List<PluginStoreCatalogVersion> versions = Arrays.stream(releaseVersions)
                .map(version -> new PluginStoreCatalogVersion(version, STORE_ROOT + "/plugins/" + code + "/" + version + ".json"))
                .toList();
        String latestJson = "json:" + code + ":" + (releaseVersions.length == 0 ? "" : releaseVersions[releaseVersions.length - 1]);
        return new PluginStoreCatalogEntry(code, STORE_ROOT + "/plugins/" + code + "/index.json", latestJson, versions);
    }

    private void stubLatestParse(PluginStoreCatalogEntry entry, PluginStorePluginDescriptor descriptor) {
        when(pluginStoreGateway.parseDescriptor(any(), eq(entry.indexUrl()), eq(entry.latestDescriptorJson())))
                .thenReturn(descriptor);
    }

    private void stubVersionFetch(PluginStoreCatalogEntry entry, String releaseVersion, PluginStorePluginDescriptor descriptor) {
        String descriptorUrl = entry.versions().stream()
                .filter(version -> version.releaseVersion().equals(releaseVersion))
                .findFirst().orElseThrow().descriptorUrl();
        when(pluginStoreGateway.fetchDescriptor(any(), eq(entry.indexUrl()), eq(descriptorUrl))).thenReturn(descriptor);
    }

    @Test
    void listsPluginsThroughStoreGateway() {
        PluginStoreCatalogEntry entry = entry("demo", "1.0.0");
        stubLegacyCatalog(entry);
        stubLatestParse(entry, descriptor("1.0.0"));

        var result = service().list();

        assertEquals(List.of("demo"), result.stream().map(item -> item.getCode()).toList());
        assertEquals("default", result.getFirst().getSourceCode());
        verify(pluginMarketSourceAppService).enabledSourceCatalogs();
        verify(pluginStoreGateway, never()).fetchCatalog(any());
    }

    @Test
    void listStillServesRemoteCatalogWhenCapabilityInactive() {
        PluginMarketSource remote = source("community", "社区", STORE_ROOT);
        PluginStoreCatalogEntry entry = entry("demo", "1.0.0");
        stubMultiSource(new PluginMarketSourceAppService.SourceCatalog(remote, snapshot(remote, entry)));
        stubLatestParse(entry, descriptor("1.0.0"));

        var result = service().list();

        assertEquals(List.of("demo"), result.stream().map(item -> item.getCode()).toList());
        assertEquals("community", result.getFirst().getSourceCode());
        verify(pluginMarketSourceAppService).enabledSourceCatalogs();
        verify(pluginStoreGateway, never()).fetchCatalog(any());
    }

    @Test
    void listMergesSourcesAndPrefersHighestVersion() {
        PluginMarketSource sourceA = source("a", "源A", "https://a.example.test/index.json");
        PluginMarketSource sourceB = source("b", "源B", "https://b.example.test/index.json");
        PluginStoreCatalogEntry entryA = entry("demo", "1.0.0");
        PluginStoreCatalogEntry entryB = entry("demo", "2.0.0");
        stubMultiSource(
                new PluginMarketSourceAppService.SourceCatalog(sourceA, snapshot(sourceA, entryA)),
                new PluginMarketSourceAppService.SourceCatalog(sourceB, snapshot(sourceB, entryB)));
        stubLatestParse(entryB, descriptor("2.0.0"));

        var result = service().list();

        assertEquals(List.of("demo"), result.stream().map(item -> item.getCode()).toList());
        assertEquals("b", result.getFirst().getSourceCode());
        assertEquals("源B", result.getFirst().getSourceName());
        assertEquals("2.0.0", result.getFirst().getDescriptor().getReleaseVersion());
    }

    @Test
    void trimsValidCodeBeforeLoadingDetail() {
        PluginStoreCatalogEntry entry = entry("demo", "1.0.0");
        stubLegacyCatalog(entry);
        stubLatestParse(entry, descriptor("1.0.0"));

        var result = service().detail(" demo ");

        assertEquals("demo", result.getCode());
        assertEquals(1, result.getVersions().size());
    }

    @Test
    void rejectsInvalidCodeWithoutCallingGateway() {
        assertThrows(BizException.class, () -> service().detail("../demo"));

        verify(pluginMarketSourceAppService, never()).ensureEnabled();
        verifyNoInteractions(pluginStoreGateway);
    }

    @Test
    void convertsMissingDetailToBusinessError() {
        stubLegacyCatalog();

        assertThrows(BizException.class, () -> service().detail("demo"));

        verify(pluginMarketSourceAppService, never()).ensureEnabled();
        verify(pluginStoreGateway, never()).fetchCatalog(any());
    }

    @Test
    void detailMarksIncompatibleAndRequiredDependencyVersionsAsNotInstallable() {
        PluginStorePluginDescriptor incompatible = descriptor("1.0.0",
                new PluginStorePluginCompatibility("^2.0.0", "^2.6.0", "^1.0.0"), List.of());
        PluginStorePluginDescriptor missingRequired = descriptor("2.0.0", null,
                List.of(new PluginStorePluginDependency("base", "^1.2.0", true)));
        PluginStoreCatalogEntry entry = entry("demo", "1.0.0", "2.0.0");
        stubLegacyCatalog(entry);
        stubVersionFetch(entry, "1.0.0", incompatible);
        stubLatestParse(entry, missingRequired);
        when(pluginAppService.list()).thenReturn(List.of());

        var result = service().detail("demo");

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
        PluginStoreCatalogEntry entry = entry("demo", "1.0.0");
        stubLegacyCatalog(entry);
        stubLatestParse(entry, descriptor);
        when(pluginAppService.list()).thenReturn(List.of());

        var result = service().detail("demo");

        assertEquals(true, result.getVersions().getFirst().isInstallable());
        assertEquals(null, result.getVersions().getFirst().getInstallDisabledReason());
        verify(pluginAppService).list();
    }

    @Test
    void installsSpecifiedStoreVersionWithoutEnablingIt() {
        PluginStorePluginDescriptor descriptor = descriptor("1.0.0");
        PluginStoreCatalogEntry entry = entry("demo", "1.0.0");
        stubLegacyCatalog(entry);
        stubLatestParse(entry, descriptor);
        PluginStoreAppService service = serviceWithUploadDirectory();

        service.install(" demo ", " 1.0.0 ", null);

        verify(pluginStoreGateway).downloadJar(any(), eq(descriptor), any());
        verify(pluginAppService).installStoreJar(any(), eq("demo"), eq("1.0.0"), eq("example.Plugin"), eq("default"));
        verifyNoMoreInteractions(pluginAppService);
    }

    @Test
    void installsFromExplicitSourceAndRecordsOrigin() {
        PluginMarketSource sourceA = source("a", "源A", "https://a.example.test/index.json");
        PluginStorePluginDescriptor descriptor = descriptor("1.0.0");
        PluginStoreCatalogEntry entryA = entry("demo", "1.0.0");
        stubMultiSource(new PluginMarketSourceAppService.SourceCatalog(sourceA, snapshot(sourceA, entryA)));
        stubLatestParse(entryA, descriptor);
        PluginStoreAppService service = serviceWithUploadDirectory();

        service.install("demo", "1.0.0", "a");

        verify(pluginStoreGateway).downloadJar(any(), eq(descriptor), any());
        verify(pluginAppService).installStoreJar(any(), eq("demo"), eq("1.0.0"), eq("example.Plugin"), eq("a"));
    }

    @Test
    void rejectsMissingStoreVersionWithoutDownloadingOrInstalling() {
        stubLegacyCatalog(entry("demo", "2.0.0"));

        assertThrows(BizException.class, () -> service().install("demo", "1.0.0", null));

        verifyNoInteractions(pluginAppService);
        verify(pluginStoreGateway, never()).downloadJar(any(), any(), any());
    }

    @Test
    void rejectsIncompatibleDescriptorsBeforeDownloading() {
        for (PluginStorePluginCompatibility compatibility : List.of(
                new PluginStorePluginCompatibility("^2.0.0", null, null),
                new PluginStorePluginCompatibility(null, "^3.0.0", null),
                new PluginStorePluginCompatibility(null, null, "^2.0.0"))) {
            PluginStorePluginDescriptor descriptor = descriptor("1.0.0", compatibility, List.of());
            PluginStoreCatalogEntry entry = entry("demo", "1.0.0");
            stubLegacyCatalog(entry);
            stubLatestParse(entry, descriptor);

            assertThrows(BizException.class, () -> service().install("demo", "1.0.0", null));
        }

        verifyNoInteractions(pluginAppService);
        verify(pluginStoreGateway, never()).downloadJar(any(), any(), any());
    }

    @Test
    void validatesRequiredLocalDependencyWithoutInstallingOptionalDependencies() {
        PluginStorePluginDescriptor descriptor = descriptor("1.0.0", new PluginStorePluginCompatibility("^1.0.0", "^2.6.0", "^1.0.0"),
                List.of(new PluginStorePluginDependency("base", "^1.2.0", true),
                        new PluginStorePluginDependency("optional", "^9.0.0", false)));
        PluginStoreCatalogEntry entry = entry("demo", "1.0.0");
        stubLegacyCatalog(entry);
        stubLatestParse(entry, descriptor);
        when(pluginAppService.list()).thenReturn(List.of(PluginModuleDTO.builder().code("base").version("1.3.0").build()));
        PluginStoreAppService service = serviceWithUploadDirectory();

        service.install("demo", "1.0.0", null);

        verify(pluginAppService).list();
        verify(pluginStoreGateway).downloadJar(any(), eq(descriptor), any());
        verify(pluginAppService).installStoreJar(any(), eq("demo"), eq("1.0.0"), eq("example.Plugin"), eq("default"));
        verifyNoMoreInteractions(pluginAppService);
    }

    @Test
    void rejectsMissingOrIncompatibleRequiredLocalDependencyBeforeDownloading() {
        PluginStorePluginDescriptor descriptor = descriptor("1.0.0", null,
                List.of(new PluginStorePluginDependency("base", "^1.2.0", true)));
        PluginStoreCatalogEntry entry = entry("demo", "1.0.0");
        stubLegacyCatalog(entry);
        stubLatestParse(entry, descriptor);
        when(pluginAppService.list()).thenReturn(List.of(PluginModuleDTO.builder().code("base").version("2.0.0").build()));

        assertThrows(BizException.class, () -> service().install("demo", "1.0.0", null));

        verify(pluginAppService).list();
        verify(pluginStoreGateway, never()).downloadJar(any(), any(), any());
    }

    @Test
    void checksInstalledPluginUpdatesUsingLatestSemanticVersionAndInstallability() {
        PluginStorePluginDescriptor latest = descriptor("2.0.0", new PluginStorePluginCompatibility("^2.0.0", null, null), List.of());
        PluginStoreCatalogEntry demoEntry = entry("demo", "1.9.0", "2.0.0", "invalid");
        PluginStoreCatalogEntry invalidEntry = entry("invalid", "2.0.0");
        stubLegacyCatalog(demoEntry, invalidEntry);
        stubVersionFetch(demoEntry, "2.0.0", latest);
        stubLatestParse(invalidEntry, descriptor("2.0.0"));
        when(pluginAppService.listInstalled()).thenReturn(List.of(
                PluginModuleDTO.builder().code("demo").version("1.0.0").build(),
                PluginModuleDTO.builder().code("missing").version("1.0.0").build(),
                PluginModuleDTO.builder().code("invalid").version("not-a-version").build()));

        var result = service().updates();

        assertEquals(2, result.size());
        assertEquals("demo", result.getFirst().getCode());
        assertEquals("2.0.0", result.getFirst().getLatestReleaseVersion());
        assertEquals(true, result.getFirst().isUpdateAvailable());
        assertEquals(false, result.getFirst().isCompatible());
        assertEquals("宿主版本不满足兼容性要求", result.getFirst().getBlockedReason());
        assertEquals("invalid", result.get(1).getCode());
        assertEquals(false, result.get(1).isUpdateAvailable());
        verify(pluginAppService, atLeastOnce()).listInstalled();
        verify(pluginStoreGateway, never()).downloadJar(any(), any(), any());
    }

    @Test
    void buildsReadOnlyUpdatePlanFromInstalledPluginsAndExactTarget() {
        PluginStorePluginDescriptor target = descriptor("2.1.0", null, List.of(
                new PluginStorePluginDependency("base", "^1.2.0", true),
                new PluginStorePluginDependency("optional", "^9.0.0", false)));
        PluginStoreCatalogEntry entry = entry("demo", "2.1.0", "3.0.0");
        stubLegacyCatalog(entry);
        stubVersionFetch(entry, "2.1.0", target);
        when(pluginAppService.listInstalled()).thenReturn(List.of(
                PluginModuleDTO.builder().code("demo").version("1.0.0").build(),
                PluginModuleDTO.builder().code("base").version("1.3.0").build(),
                PluginModuleDTO.builder().code("hard-client").version("1.0.0")
                        .dependencies(List.of("demo"))
                        .status(online.yudream.base.domain.platform.plugin.enumerate.PluginStatus.ENABLED).build(),
                PluginModuleDTO.builder().code("soft-client").version("1.0.0")
                        .softDependencies(List.of("demo"))
                        .status(online.yudream.base.domain.platform.plugin.enumerate.PluginStatus.ENABLED).build()));

        var result = service().updatePlan("demo", " 2.1.0 ");

        assertEquals("MAJOR", result.getChangeType());
        assertEquals("2.1.0", result.getToVersion());
        assertEquals(List.of("base"), result.getRequiredDependencies().stream().map(item -> item.getCode()).toList());
        assertEquals(List.of("optional"), result.getOptionalDependencies().stream().map(item -> item.getCode()).toList());
        assertEquals(List.of("hard-client", "soft-client"), result.getAffectedEnabledPlugins());
        assertEquals(true, result.isRequiresRestart());
        assertEquals(null, result.getBlockedReason());
        assertEquals(List.of("可选依赖 optional 不可用"), result.getWarnings());
        verify(pluginAppService, atLeastOnce()).listInstalled();
        verify(pluginAppService, never()).installStoreJar(any(), any(), any(), any(), any());
        verify(pluginStoreGateway, never()).downloadJar(any(), any(), any());
    }

    @Test
    void skipsInvalidInstalledPluginWhenBuildingUpdatePlans() {
        PluginStoreCatalogEntry entry = entry("demo", "2.0.0");
        stubLegacyCatalog(entry);
        stubLatestParse(entry, descriptor("2.0.0"));
        when(pluginAppService.listInstalled()).thenReturn(List.of(
                PluginModuleDTO.builder().code("../broken").version("1.0.0").build(),
                PluginModuleDTO.builder().code("demo").version("1.0.0").build()));

        var result = service().updatePlans();

        assertEquals(List.of("demo"), result.stream().map(item -> item.getCode()).toList());
        verify(pluginStoreGateway, never()).fetchCatalog(any());
    }

    @Test
    void buildsDefaultPlansUsingHighestParsableVersionWithoutSideEffects() {
        PluginStoreCatalogEntry entry = entry("demo", "1.2.0", "2.0.0");
        stubLegacyCatalog(entry);
        stubLatestParse(entry, descriptor("2.0.0"));
        when(pluginAppService.listInstalled()).thenReturn(List.of(
                PluginModuleDTO.builder().code("demo").version("1.0.0").build(),
                PluginModuleDTO.builder().code("missing").version("1.0.0").build()));

        var result = service().updatePlans();

        assertEquals(1, result.size());
        assertEquals("demo", result.getFirst().getCode());
        assertEquals("2.0.0", result.getFirst().getToVersion());
        assertEquals("MAJOR", result.getFirst().getChangeType());
        verify(pluginAppService, atLeastOnce()).listInstalled();
        verify(pluginAppService, never()).installStoreJar(any(), any(), any(), any(), any());
        verify(pluginStoreGateway, never()).downloadJar(any(), any(), any());
    }

    @Test
    void rejectsBlockedUpdateBeforeDownloading() {
        PluginStorePluginDescriptor target = descriptor("2.0.0", null,
                List.of(new PluginStorePluginDependency("base", "^1.0.0", true)));
        PluginStoreCatalogEntry entry = entry("demo", "2.0.0");
        stubLegacyCatalog(entry);
        stubLatestParse(entry, target);
        when(pluginAppService.listInstalled()).thenReturn(List.of(PluginModuleDTO.builder().code("demo").version("1.0.0").build()));

        assertThrows(BizException.class, () -> service().update("demo", "2.0.0", null));

        verify(pluginStoreGateway, never()).downloadJar(any(), any(), any());
        verify(pluginAppService, never()).updateStoreJar(any(), any(), any(), any(), any());
    }

    @Test
    void updatesRunningPluginThroughControlledUpdateFlowWithoutHotReenable() {
        PluginStorePluginDescriptor target = descriptor("2.0.0");
        PluginStoreCatalogEntry entry = entry("demo", "2.0.0");
        stubLegacyCatalog(entry);
        stubLatestParse(entry, target);
        when(pluginAppService.listInstalled()).thenReturn(List.of(PluginModuleDTO.builder().code("demo").version("1.0.0").loaded(true).build()));
        PluginStoreAppService service = serviceWithUploadDirectory();

        var result = service.update("demo", "2.0.0", null);

        assertEquals(true, result.isRequiresRestart());
        verify(pluginStoreGateway).downloadJar(any(), eq(target), any());
        verify(pluginAppService).updateStoreJar(any(), eq("demo"), eq("2.0.0"), eq("example.Plugin"), eq("default"));
        verify(pluginAppService, never()).enable(any());
        verify(pluginAppService, never()).disable(any());
        verify(pluginAppService, never()).unload(any());
    }

    @Test
    void installsLocalStructuredVersionByCopyingJar() throws Exception {
        java.nio.file.Path sourceJar = java.nio.file.Files.createTempFile("local-plugin-", ".jar");
        java.nio.file.Files.writeString(sourceJar, "plugin-jar");
        String sha256 = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(sourceJar)));
        PluginStoreStructuredVersion structured = new PluginStoreStructuredVersion(
                "1.0.0", "local:demo/1.0.0/plugin.jar", sha256, "example.Plugin", "Demo", null,
                10L, "效率工具", List.of("demo"), java.util.Map.of(), List.of());
        PluginStoreCatalogEntry entry = new PluginStoreCatalogEntry("demo", "local:demo", null, List.of(), List.of(structured));
        PluginMarketSource source = source("default", "本机插件市场", null);
        stubMultiSource(new PluginMarketSourceAppService.SourceCatalog(source, snapshot(source, entry)));
        when(pluginMarketSourceAppService.resolveLocalJar("local:demo/1.0.0/plugin.jar")).thenReturn(sourceJar);
        PluginStoreAppService service = serviceWithUploadDirectory();

        service.install("demo", "1.0.0", "default");

        verify(pluginStoreGateway, never()).downloadJar(any(), any(), any());
        verify(pluginAppService).installStoreJar(any(), eq("demo"), eq("1.0.0"), eq("example.Plugin"), eq("default"));
        java.nio.file.Files.deleteIfExists(sourceJar);
    }

    @Test
    void excludesNonUpgradePlansAndRejectsTheirExecution() {
        PluginStoreCatalogEntry entry = entry("demo", "2.0.0");
        stubLegacyCatalog(entry);
        stubLatestParse(entry, descriptor("2.0.0"));
        when(pluginAppService.listInstalled()).thenReturn(List.of(PluginModuleDTO.builder().code("demo").version("2.0.0").build()));

        assertEquals(List.of(), service().updatePlans());
        assertThrows(BizException.class, () -> service().update("demo", "2.0.0", null));
        verify(pluginStoreGateway, never()).downloadJar(any(), any(), any());
    }

    @Test
    void rollsBackUsingOnlyLocalBackupWithoutMarketplaceAccessOrEnabling() {
        when(pluginAppService.listInstalled()).thenReturn(List.of(
                PluginModuleDTO.builder().code("demo").version("2.0.0").build()));
        when(pluginAppService.rollbackStoreJar("demo")).thenReturn(List.of(
                PluginModuleDTO.builder().code("demo").version("1.0.0").build()));

        var result = service().rollback("demo");

        assertEquals(true, result.isRequiresRestart());
        verify(pluginAppService).rollbackStoreJar("demo");
        verifyNoInteractions(pluginStoreGateway);
        verifyNoInteractions(pluginMarketSourceAppService);
        verify(pluginAppService, never()).enable(any());
    }

    private PluginMarketSource source(String code, String name, String rootUrl) {
        return PluginMarketSource.builder()
                .code(code)
                .name(name)
                .rootUrl(rootUrl)
                .enabled(true)
                .builtIn(false)
                .sortOrder(0)
                .build();
    }

    private online.yudream.base.domain.platform.plugin.valobj.PluginMarketSourceSnapshot snapshot(PluginMarketSource source,
                                                                                                  PluginStoreCatalogEntry... entries) {
        return new online.yudream.base.domain.platform.plugin.valobj.PluginMarketSourceSnapshot(
                source.getId(), null, List.of(entries));
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
