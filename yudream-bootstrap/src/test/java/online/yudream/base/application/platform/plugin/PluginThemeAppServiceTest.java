package online.yudream.base.application.platform.plugin;

import online.yudream.base.application.platform.cms.service.CmsPresetAppService;
import online.yudream.base.application.platform.plugin.dto.PluginThemeDTO;
import online.yudream.base.application.platform.plugin.dto.PluginThemeOverviewDTO;
import online.yudream.base.application.platform.plugin.service.PluginThemeAppService;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendAssetInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginThemeInfo;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginThemeAppServiceTest {

    @Mock
    private PluginRuntimeGateway pluginRuntimeGateway;

    @Mock
    private CmsPresetAppService cmsPresetAppService;

    private InMemorySettingRepo settingRepo;
    private PluginThemeAppService service;

    @BeforeEach
    void setUp() {
        settingRepo = new InMemorySettingRepo();
        service = new PluginThemeAppService(pluginRuntimeGateway, settingRepo, cmsPresetAppService);
    }

    @Test
    void activateWritesDeclaredScopesAndActiveThemesResolve() {
        stubTheme(theme("neco", Set.of("SITE", "ADMIN")));

        service.activate("neco");

        Map<String, PluginThemeDTO> active = service.activeThemes();
        assertThat(active).containsOnlyKeys("SITE", "ADMIN");
        assertThat(active.get("SITE").getPluginCode()).isEqualTo("neco");
        assertThat(active.get("ADMIN").getName()).isEqualTo("Neco 主题");
        assertThat(settingRepo.findByKey("pluginTheme.active.site")).hasValueSatisfying(
                setting -> assertThat(setting.getValue()).isEqualTo("neco"));
    }

    @Test
    void activateWithoutThemeIsNoOp() {
        service.activate("plain-plugin");

        assertThat(settingRepo.findAll()).isEmpty();
        assertThat(service.activeThemes()).isEmpty();
    }

    @Test
    void clearActivationOnlyClearsSlotsOwnedByPlugin() {
        stubTheme(theme("neco", Set.of("SITE", "ADMIN")));
        stubTheme(theme("pixel", Set.of("SITE")));
        service.activate("neco");
        service.activate("pixel");

        service.clearActivation("pixel");

        assertThat(readSlot("SITE")).isNull();
        assertThat(readSlot("ADMIN")).isEqualTo("neco");
    }

    @Test
    void clearActivationUnpublishesThemePagesWhenSiteSlotReleased() {
        stubTheme(theme("neco", Set.of("SITE", "ADMIN")));
        service.activate("neco");

        service.clearActivation("neco");

        verify(cmsPresetAppService).unpublishPluginPages("neco");
        verify(cmsPresetAppService).restorePluginHomepage("neco");
    }

    @Test
    void clearActivationSkipsPageUnpublishWhenOnlyAdminSlotReleased() {
        stubTheme(theme("pixel", Set.of("ADMIN")));
        service.activate("pixel");

        service.clearActivation("pixel");

        verify(cmsPresetAppService, never()).unpublishPluginPages(org.mockito.ArgumentMatchers.anyString());
        verify(cmsPresetAppService, never()).restorePluginHomepage(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void clearActivationStillClearsSlotWhenPageUnpublishFails() {
        stubTheme(theme("neco", Set.of("SITE")));
        service.activate("neco");
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(cmsPresetAppService).unpublishPluginPages("neco");

        service.clearActivation("neco");

        assertThat(readSlot("SITE")).isNull();
    }

    @Test
    void activeThemesDropsSlotWhosePluginNoLongerServesScope() {
        stubTheme(theme("neco", Set.of("SITE")));
        service.activate("neco");
        when(pluginRuntimeGateway.theme("neco")).thenReturn(Optional.empty());

        assertThat(service.activeThemes()).isEmpty();
    }

    @Test
    void reconcileClearsInvalidSlotAndBackfillsSingleCandidate() {
        PluginThemeInfo neco = theme("neco", Set.of("SITE"));
        stubTheme(neco);
        writeSlot("SITE", "ghost-plugin");
        when(pluginRuntimeGateway.themes()).thenReturn(List.of(neco));

        service.reconcileAfterRestore();

        assertThat(readSlot("SITE")).isEqualTo("neco");
    }

    @Test
    void reconcileKeepsValidSlotAndLeavesMultiCandidateScopeAlone() {
        PluginThemeInfo neco = theme("neco", Set.of("SITE"));
        PluginThemeInfo pixel = theme("pixel", Set.of("SITE"));
        stubTheme(neco);
        stubTheme(pixel);
        writeSlot("SITE", "pixel");
        when(pluginRuntimeGateway.themes()).thenReturn(List.of(neco, pixel));

        service.reconcileAfterRestore();

        assertThat(readSlot("SITE")).isEqualTo("pixel");
    }

    @Test
    void overviewListsEnabledThemesWithActiveSlots() {
        PluginThemeInfo neco = theme("neco", Set.of("SITE"));
        PluginThemeInfo pixel = theme("pixel", Set.of("ADMIN"));
        stubTheme(neco);
        stubTheme(pixel);
        when(pluginRuntimeGateway.themes()).thenReturn(List.of(neco, pixel));
        service.activate("pixel");

        PluginThemeOverviewDTO overview = service.overview();

        assertThat(overview.getThemes()).hasSize(2);
        assertThat(overview.getActive()).containsExactlyEntriesOf(Map.of("ADMIN", "pixel"));
    }

    @Test
    void activateSiteThemeWithHomePresetImportsAndAppliesPreset() {
        PluginThemeInfo neco = new PluginThemeInfo("neco", "neco", "Neco 主题", "像素风主题",
                Set.of("SITE"), List.of("theme/neco.css"), "", "home-preset.json", "rev-1");
        stubTheme(neco);
        String presetJson = "{\"title\":\"像素首页\"}";
        when(pluginRuntimeGateway.frontendAsset("neco", "home-preset.json"))
                .thenReturn(Optional.of(new PluginFrontendAssetInfo("home-preset.json", "application/json",
                        presetJson.getBytes(StandardCharsets.UTF_8))));

        service.activate("neco");

        verify(cmsPresetAppService).importPluginPreset("neco", "Neco 主题", presetJson);
    }

    @Test
    void activateSiteThemeWithoutHomePresetSkipsPresetImport() {
        stubTheme(theme("neco", Set.of("SITE")));

        service.activate("neco");

        verify(cmsPresetAppService, never()).importPluginPreset(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void activateStillSucceedsWhenPresetAssetMissing() {
        PluginThemeInfo neco = new PluginThemeInfo("neco", "neco", "Neco 主题", "像素风主题",
                Set.of("SITE"), List.of("theme/neco.css"), "", "home-preset.json", "rev-1");
        stubTheme(neco);
        when(pluginRuntimeGateway.frontendAsset("neco", "home-preset.json")).thenReturn(Optional.empty());

        service.activate("neco");

        assertThat(readSlot("SITE")).isEqualTo("neco");
        verify(cmsPresetAppService, never()).importPluginPreset(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    private PluginThemeInfo theme(String pluginCode, Set<String> scopes) {
        return new PluginThemeInfo(pluginCode, pluginCode, "Neco 主题", "像素风主题",
                scopes, List.of("theme/" + pluginCode + ".css"), "", "", "rev-1");
    }

    private void stubTheme(PluginThemeInfo theme) {
        org.mockito.Mockito.lenient()
                .when(pluginRuntimeGateway.theme(theme.pluginCode())).thenReturn(Optional.of(theme));
    }

    private void writeSlot(String scope, String pluginCode) {
        settingRepo.save(Setting.builder()
                .key("pluginTheme.active." + scope.toLowerCase())
                .value(pluginCode)
                .build());
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
