package online.yudream.base.application.platform.plugin;

import online.yudream.base.application.platform.plugin.service.PluginAppService;
import online.yudream.base.application.platform.plugin.service.PluginMenuProjectionService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.enumerate.PluginStatus;
import online.yudream.base.domain.platform.plugin.repo.PluginModuleRepo;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginDescriptorInfo;
import online.yudream.base.domain.system.user.service.PermissionDomainService;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginAppServiceTest {

    private static final String PLUGIN_CODE = "demo-plugin";
    private static final String OLD_BYTES = "old-plugin-jar";
    private static final String NEW_BYTES = "new-plugin-jar";

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

    @Mock
    private RoleRepo roleRepo;

    private PluginAppService service;

    @BeforeEach
    void setUp() {
        service = new PluginAppService(
                pluginModuleRepo,
                pluginRuntimeGateway,
                permissionDomainService,
                pluginMenuProjectionService,
                roleRepo
        );
        ReflectionTestUtils.setField(service, "uploadDirectory", tempDir.toString());
    }

    @Test
    void installStoreJarBacksUpActiveJarAndReplacesItWithStagedContent() throws IOException {
        Path active = writeJar("demo-plugin.jar", OLD_BYTES);
        Path staged = writeJar("marketplace-download.jar", NEW_BYTES);
        PluginModule module = module(active, "1.0.0", "Old plugin");
        when(pluginRuntimeGateway.discover()).thenReturn(List.of());
        when(pluginModuleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubRepository(module);
        stubDescriptors(Map.of(
                OLD_BYTES, descriptor("1.0.0", "Old plugin", active),
                NEW_BYTES, descriptor("2.0.0", "New plugin", staged)
        ));

        service.installStoreJar(staged, PLUGIN_CODE, "2.0.0", "example.NewPlugin");

        Path backup = Path.of(module.getBackupJarPath());
        assertThat(backup.toAbsolutePath().normalize().startsWith(tempDir.toAbsolutePath().normalize())).isFalse();
        assertThat(Files.readString(active)).isEqualTo(NEW_BYTES);
        assertThat(Files.readString(backup)).isEqualTo(OLD_BYTES);
        assertThat(staged).doesNotExist();
        assertThat(module.getBackupJarPath()).isEqualTo(backup.toAbsolutePath().normalize().toString());
        assertThat(module.getBackupName()).isEqualTo("Old plugin");
        assertThat(module.getBackupPluginVersion()).isEqualTo("1.0.0");
        assertThat(module.getBackupMainClass()).isEqualTo("example.OldPlugin");
        assertThat(module.getBackupSha256()).isEqualTo(sha256(backup));
        verify(pluginRuntimeGateway).describe(staged);
        verify(pluginRuntimeGateway).discover();
    }

    @Test
    void marketplaceUpdateStopsTransitiveDependentsAndRecordsRestoreIntentWithoutLiveReenable() throws IOException {
        Path active = writeJar("demo-plugin.jar", OLD_BYTES);
        Path staged = writeJar("marketplace-download.jar", NEW_BYTES);
        PluginModule module = module(active, "1.0.0", "Old plugin");
        module.markEnabled();
        PluginModule dependent = PluginModule.builder()
                .code("dependent-plugin")
                .name("Dependent")
                .pluginVersion("1.0.0")
                .dependencies(List.of(PLUGIN_CODE))
                .status(PluginStatus.ENABLED)
                .build();
        when(pluginRuntimeGateway.discover()).thenReturn(List.of());
        when(pluginModuleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubRepository(module, dependent);
        stubDescriptors(Map.of(
                OLD_BYTES, descriptor("1.0.0", "Old plugin", active),
                NEW_BYTES, descriptor("2.0.0", "New plugin", staged)
        ));
        when(pluginRuntimeGateway.enabled(PLUGIN_CODE)).thenReturn(true);
        when(pluginRuntimeGateway.loaded(PLUGIN_CODE)).thenReturn(true);
        when(pluginRuntimeGateway.enabled("dependent-plugin")).thenReturn(true);
        when(pluginRuntimeGateway.loaded("dependent-plugin")).thenReturn(true);

        service.installStoreJar(staged, PLUGIN_CODE, "2.0.0", "example.NewPlugin");

        InOrder lifecycle = org.mockito.Mockito.inOrder(pluginRuntimeGateway);
        lifecycle.verify(pluginRuntimeGateway).disable("dependent-plugin");
        lifecycle.verify(pluginRuntimeGateway).unload("dependent-plugin");
        lifecycle.verify(pluginRuntimeGateway).disable(PLUGIN_CODE);
        lifecycle.verify(pluginRuntimeGateway).unload(PLUGIN_CODE);
        assertThat(module.getRestoreIntentActive()).isTrue();
        assertThat(dependent.getRestoreIntentActive()).isTrue();
        assertThat(module.getStatus()).isEqualTo(PluginStatus.INSTALLED);
        assertThat(dependent.getStatus()).isEqualTo(PluginStatus.INSTALLED);
        verify(pluginRuntimeGateway, never()).enable(any());
        verify(pluginRuntimeGateway, never()).load(any());
    }

    @Test
    void restoreEnabledPluginsLoadsAndEnablesInstalledModuleWithRestoreIntentThenClearsIntent() throws IOException {
        PluginModule module = module(writeJar("demo-plugin.jar", NEW_BYTES), "1.0.0", "Demo plugin");
        module.setRestoreIntentActive(true);
        when(pluginRuntimeGateway.discover()).thenReturn(List.of());
        when(pluginRuntimeGateway.loaded(PLUGIN_CODE)).thenReturn(false, true);
        when(pluginRuntimeGateway.enabled(PLUGIN_CODE)).thenReturn(false, false, false, true);
        when(pluginModuleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubRepository(module);

        service.restoreEnabledPlugins();

        InOrder lifecycle = org.mockito.Mockito.inOrder(pluginRuntimeGateway);
        lifecycle.verify(pluginRuntimeGateway).load(module);
        lifecycle.verify(pluginRuntimeGateway).enable(module);
        assertThat(module.getStatus()).isEqualTo(PluginStatus.ENABLED);
        assertThat(module.getRestoreIntentActive()).isFalse();
    }

    @Test
    void manualDisableAndUnloadClearRestoreIntentAndPreventRestore() throws IOException {
        PluginModule module = module(writeJar("demo-plugin.jar", NEW_BYTES), "1.0.0", "Demo plugin");
        module.markEnabled();
        module.setRestoreIntentActive(true);
        when(pluginRuntimeGateway.discover()).thenReturn(List.of());
        when(pluginRuntimeGateway.enabled(PLUGIN_CODE)).thenReturn(false);
        when(pluginModuleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubRepository(module);

        service.disable(PLUGIN_CODE);
        service.unload(PLUGIN_CODE);
        service.restoreEnabledPlugins();

        assertThat(module.getStatus()).isEqualTo(PluginStatus.INSTALLED);
        assertThat(module.getRestoreIntentActive()).isFalse();
        verify(pluginRuntimeGateway, never()).load(any());
        verify(pluginRuntimeGateway, never()).enable(any());
    }

    @Test
    void rollbackStoreJarRestoresStoppedPluginAndSwapsControlledBackupMetadata() throws IOException {
        Path active = writeJar("demo-plugin.jar", NEW_BYTES);
        Path backup = writeJar("../.plugin-rollback/demo-plugin-1.0.0.jar", OLD_BYTES);
        PluginModule module = module(active, "2.0.0", "New plugin");
        setBackup(module, backup, "1.0.0", "Old plugin", PluginStatus.DISABLED);
        PluginModule dependent = PluginModule.builder()
                .code("dependent-plugin")
                .name("Dependent")
                .pluginVersion("1.0.0")
                .dependencies(List.of(PLUGIN_CODE))
                .status(PluginStatus.INSTALLED)
                .build();
        when(pluginRuntimeGateway.discover()).thenReturn(List.of());
        when(pluginModuleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubRepository(module, dependent);
        stubDescriptors(Map.of(
                OLD_BYTES, descriptor("1.0.0", "Old plugin", backup),
                NEW_BYTES, descriptor("2.0.0", "New plugin", active)
        ));

        service.rollbackStoreJar(PLUGIN_CODE, "1.0.0");

        assertThat(Files.readString(active)).isEqualTo(OLD_BYTES);
        assertThat(Files.readString(backup)).isEqualTo(NEW_BYTES);
        assertThat(module.getName()).isEqualTo("Old plugin");
        assertThat(module.getPluginVersion()).isEqualTo("1.0.0");
        assertThat(module.getMainClass()).isEqualTo("example.OldPlugin");
        assertThat(module.getStatus()).isEqualTo(PluginStatus.INSTALLED);
        assertThat(module.getBackupName()).isEqualTo("New plugin");
        assertThat(module.getBackupPluginVersion()).isEqualTo("2.0.0");
        assertThat(module.getBackupMainClass()).isEqualTo("example.NewPlugin");
        assertThat(module.getBackupSha256()).isEqualTo(sha256(backup));
        verify(pluginRuntimeGateway).discover();
        verify(pluginRuntimeGateway, never()).enable(any());
        verify(pluginRuntimeGateway, never()).load(any());
        verify(pluginRuntimeGateway, never()).disable(anyString());
        verify(pluginRuntimeGateway, never()).unload(anyString());
    }

    @Test
    void rollbackStoreJarRejectsWhenDependentIsRunningWithoutLifecycleCalls() throws IOException {
        Path active = writeJar("demo-plugin.jar", NEW_BYTES);
        Path backup = writeJar("../.plugin-rollback/demo-plugin-1.0.0.jar", OLD_BYTES);
        PluginModule module = module(active, "2.0.0", "New plugin");
        setBackup(module, backup, "1.0.0", "Old plugin", PluginStatus.INSTALLED);
        PluginModule dependent = PluginModule.builder()
                .code("dependent-plugin")
                .name("Dependent")
                .pluginVersion("1.0.0")
                .dependencies(List.of(PLUGIN_CODE))
                .status(PluginStatus.ENABLED)
                .build();
        stubRepository(module, dependent);

        assertThatThrownBy(() -> service.rollbackStoreJar(PLUGIN_CODE))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("插件或其依赖方正在运行");

        assertThat(Files.readString(active)).isEqualTo(NEW_BYTES);
        assertThat(Files.readString(backup)).isEqualTo(OLD_BYTES);
        verify(pluginRuntimeGateway, never()).enable(any());
        verify(pluginRuntimeGateway, never()).load(any());
        verify(pluginRuntimeGateway, never()).disable(anyString());
        verify(pluginRuntimeGateway, never()).unload(anyString());
        verify(pluginRuntimeGateway, never()).discover();
    }

    private Path writeJar(String filename, String content) throws IOException {
        Path jar = tempDir.resolve(filename);
        Files.createDirectories(jar.getParent());
        return Files.writeString(jar, content, StandardCharsets.UTF_8);
    }

    private PluginModule module(Path active, String version, String name) {
        return PluginModule.builder()
                .code(PLUGIN_CODE)
                .name(name)
                .pluginVersion(version)
                .description(name + " description")
                .mainClass(mainClass(name))
                .jarPath(active.toAbsolutePath().normalize().toString())
                .dependencies(List.of())
                .softDependencies(List.of())
                .status(PluginStatus.INSTALLED)
                .build();
    }

    private void setBackup(PluginModule module, Path backup, String version, String name, PluginStatus status) {
        module.setBackupJarPath(backup.toAbsolutePath().normalize().toString());
        module.setBackupName(name);
        module.setBackupPluginVersion(version);
        module.setBackupDescription(name + " description");
        module.setBackupMainClass(mainClass(name));
        module.setBackupSha256(sha256(backup));
        module.setBackupDependencies(List.of());
        module.setBackupSoftDependencies(List.of());
        module.setBackupStatus(status);
        module.setBackupMenusInitialized(false);
    }

    private void stubRepository(PluginModule module, PluginModule... others) {
        List<PluginModule> modules = new java.util.ArrayList<>();
        modules.add(module);
        modules.addAll(List.of(others));
        when(pluginModuleRepo.findByCode(anyString())).thenAnswer(invocation ->
                PLUGIN_CODE.equals(invocation.getArgument(0)) ? Optional.of(module) : Optional.empty());
        when(pluginModuleRepo.findAll()).thenReturn(modules);
    }

    private void stubDescriptors(Map<String, PluginDescriptorInfo> descriptors) {
        when(pluginRuntimeGateway.describe(any())).thenAnswer(invocation -> {
            Path jar = invocation.getArgument(0);
            return Optional.ofNullable(descriptors.get(Files.readString(jar)));
        });
    }

    private PluginDescriptorInfo descriptor(String version, String name, Path jar) {
        return new PluginDescriptorInfo(
                PLUGIN_CODE,
                name,
                version,
                name + " description",
                mainClass(name),
                jar.toAbsolutePath().normalize().toString(),
                List.of(),
                List.of()
        );
    }

    private String mainClass(String name) {
        return "example." + name.replace(" plugin", "").replace(" ", "") + "Plugin";
    }

    private String sha256(Path file) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
