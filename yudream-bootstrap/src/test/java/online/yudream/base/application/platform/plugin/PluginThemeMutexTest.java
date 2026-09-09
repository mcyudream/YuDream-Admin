package online.yudream.base.application.platform.plugin;

import online.yudream.base.application.platform.plugin.service.PluginAppService;
import online.yudream.base.application.platform.plugin.service.PluginMenuProjectionService;
import online.yudream.base.application.platform.plugin.service.PluginThemeAppService;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.enumerate.PluginStatus;
import online.yudream.base.domain.platform.plugin.repo.PluginModuleRepo;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginThemeInfo;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.service.PermissionDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 主题互斥：启用同 scope 主题插件自动顶替旧主题插件，禁用/卸载释放激活位。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PluginThemeMutexTest {

    private static final String THEME_A = "theme-a";
    private static final String THEME_B = "theme-b";

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

    private final Set<String> runtimeEnabled = new HashSet<>();
    private final Set<String> runtimeLoaded = new HashSet<>();
    private InMemorySettingRepo settingRepo;
    private PluginThemeAppService themeAppService;
    private PluginAppService service;

    @BeforeEach
    void setUp() {
        settingRepo = new InMemorySettingRepo();
        themeAppService = new PluginThemeAppService(pluginRuntimeGateway, settingRepo);
        service = new PluginAppService(
                pluginModuleRepo,
                pluginRuntimeGateway,
                permissionDomainService,
                pluginMenuProjectionService,
                roleRepo,
                themeAppService
        );
        when(pluginRuntimeGateway.enabled(anyString())).thenAnswer(invocation -> runtimeEnabled.contains(invocation.getArgument(0)));
        when(pluginRuntimeGateway.loaded(anyString())).thenAnswer(invocation -> runtimeLoaded.contains(invocation.getArgument(0)));
        doAnswer(invocation -> {
            PluginModule module = invocation.getArgument(0);
            runtimeLoaded.add(module.getCode());
            runtimeEnabled.add(module.getCode());
            return null;
        }).when(pluginRuntimeGateway).enable(any());
        doAnswer(invocation -> {
            runtimeLoaded.add(((PluginModule) invocation.getArgument(0)).getCode());
            return null;
        }).when(pluginRuntimeGateway).load(any());
        doAnswer(invocation -> {
            runtimeEnabled.remove(invocation.getArgument(0));
            return null;
        }).when(pluginRuntimeGateway).disable(anyString());
        when(pluginRuntimeGateway.permissions(anyString())).thenReturn(List.of());
        when(pluginRuntimeGateway.frontendModules()).thenReturn(List.of());
        when(pluginRuntimeGateway.themes()).thenAnswer(invocation -> {
            Map<String, PluginThemeInfo> themes = themeInfos();
            return runtimeEnabled.stream().filter(themes::containsKey).map(themes::get).toList();
        });
        when(pluginRuntimeGateway.theme(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(themeInfos().get(invocation.getArgument(0)))
                        .filter(info -> runtimeEnabled.contains(info.pluginCode())));
        when(pluginModuleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void enablingThemePluginAutoDisablesConflictingThemeAndTakesOverSlot() throws IOException {
        PluginModule themeA = module(THEME_A, PluginStatus.ENABLED);
        PluginModule themeB = module(THEME_B, PluginStatus.INSTALLED);
        runtimeEnabled.add(THEME_A);
        runtimeLoaded.add(THEME_A);
        stubRepository(themeA, themeB);
        themeAppService.activate(THEME_A);

        service.enable(THEME_B);

        verify(pluginRuntimeGateway).disable(THEME_A);
        assertThat(themeA.getStatus()).isEqualTo(PluginStatus.DISABLED);
        assertThat(themeB.getStatus()).isEqualTo(PluginStatus.ENABLED);
        assertThat(readSlot("SITE")).isEqualTo(THEME_B);
    }

    @Test
    void enablingNonThemePluginKeepsExistingActivation() throws IOException {
        PluginModule themeA = module(THEME_A, PluginStatus.ENABLED);
        PluginModule plain = module("plain-plugin", PluginStatus.INSTALLED);
        runtimeEnabled.add(THEME_A);
        runtimeLoaded.add(THEME_A);
        stubRepository(themeA, plain);
        themeAppService.activate(THEME_A);

        service.enable("plain-plugin");

        assertThat(readSlot("SITE")).isEqualTo(THEME_A);
        assertThat(runtimeEnabled).contains(THEME_A, "plain-plugin");
    }

    @Test
    void disableReleasesOwnedActivationSlot() throws IOException {
        PluginModule themeB = module(THEME_B, PluginStatus.ENABLED);
        runtimeEnabled.add(THEME_B);
        runtimeLoaded.add(THEME_B);
        stubRepository(themeB);
        themeAppService.activate(THEME_B);
        assertThat(readSlot("SITE")).isEqualTo(THEME_B);

        service.disable(THEME_B);

        assertThat(readSlot("SITE")).isNull();
        assertThat(themeB.getStatus()).isEqualTo(PluginStatus.DISABLED);
    }

    private Map<String, PluginThemeInfo> themeInfos() {
        Map<String, PluginThemeInfo> themes = new LinkedHashMap<>();
        themes.put(THEME_A, themeInfo(THEME_A));
        themes.put(THEME_B, themeInfo(THEME_B));
        return themes;
    }

    private PluginThemeInfo themeInfo(String pluginCode) {
        return new PluginThemeInfo(pluginCode, pluginCode, pluginCode + " 主题", "",
                Set.of("SITE"), List.of("theme/" + pluginCode + ".css"), "", "rev-1");
    }

    private PluginModule module(String code, PluginStatus status) throws IOException {
        Path jar = Files.writeString(tempDir.resolve(code + ".jar"), "jar-" + code);
        return PluginModule.builder()
                .code(code)
                .name(code)
                .pluginVersion("1.0.0")
                .jarPath(jar.toAbsolutePath().normalize().toString())
                .dependencies(List.of())
                .softDependencies(List.of())
                .status(status)
                .build();
    }

    private void stubRepository(PluginModule... modules) {
        List<PluginModule> all = new ArrayList<>(List.of(modules));
        when(pluginModuleRepo.findAll()).thenReturn(all);
        when(pluginModuleRepo.findByCode(anyString())).thenAnswer(invocation ->
                all.stream().filter(module -> module.getCode().equals(invocation.getArgument(0))).findFirst());
    }

    private String readSlot(String scope) {
        return settingRepo.findByKey("pluginTheme.active." + scope.toLowerCase())
                .map(Setting::getValue)
                .filter(value -> !value.isBlank())
                .orElse(null);
    }

    static class InMemorySettingRepo implements SettingRepo {

        private final Map<String, Setting> store = new LinkedHashMap<>();

        @Override
        public Setting save(Setting setting) {
            store.put(setting.getKey(), setting);
            return setting;
        }

        @Override
        public Optional<Setting> findByKey(String key) {
            return Optional.ofNullable(store.get(key));
        }

        @Override
        public boolean existsByKey(String key) {
            return store.containsKey(key);
        }

        @Override
        public List<Setting> findByCategory(String category) {
            return store.values().stream()
                    .filter(setting -> category.equals(setting.getCategory()))
                    .toList();
        }

        @Override
        public List<Setting> findAll() {
            return new ArrayList<>(store.values());
        }
    }
}
