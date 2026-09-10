package online.yudream.base.application.platform.theme;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.cms.dto.HomePagePresetDTO;
import online.yudream.base.application.platform.cms.service.CmsPresetAppService;
import online.yudream.base.application.platform.plugin.dto.PluginThemeDTO;
import online.yudream.base.application.platform.plugin.service.PluginAppService;
import online.yudream.base.application.platform.plugin.service.PluginThemeAppService;
import online.yudream.base.application.platform.theme.dto.ThemeCenterOverviewDTO;
import online.yudream.base.application.platform.theme.dto.ThemeCenterThemeCardDTO;
import online.yudream.base.application.platform.theme.service.ThemeCenterAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.cms.aggregate.HomePageLayout;
import online.yudream.base.domain.platform.cms.repo.HomePageLayoutRepo;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.repo.PluginModuleRepo;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendAssetInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginThemeInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThemeCenterAppServiceTest {

    @Mock
    private PluginRuntimeGateway pluginRuntimeGateway;

    @Mock
    private PluginModuleRepo pluginModuleRepo;

    @Mock
    private PluginAppService pluginAppService;

    @Mock
    private PluginThemeAppService pluginThemeAppService;

    @Mock
    private CmsPresetAppService cmsPresetAppService;

    @Mock
    private CapabilityModuleRepo capabilityModuleRepo;

    @Mock
    private HomePageLayoutRepo homePageLayoutRepo;

    private ThemeCenterAppService service;

    @BeforeEach
    void setUp() {
        service = new ThemeCenterAppService(pluginRuntimeGateway, pluginModuleRepo, pluginAppService,
                pluginThemeAppService, cmsPresetAppService, capabilityModuleRepo, homePageLayoutRepo,
                new ObjectMapper());
        lenient().when(pluginRuntimeGateway.themes()).thenReturn(List.of());
        lenient().when(pluginModuleRepo.findAll()).thenReturn(List.of());
        lenient().when(pluginThemeAppService.activeThemes()).thenReturn(Map.of());
        lenient().when(capabilityModuleRepo.findByCode("cms")).thenReturn(Optional.empty());
        lenient().when(homePageLayoutRepo.findAll()).thenReturn(List.of());
    }

    @Test
    void overviewListsBuiltinCardActiveWhenNoSiteThemeActive() {
        ThemeCenterOverviewDTO overview = service.overview();

        assertThat(overview.getThemes()).hasSize(1);
        ThemeCenterThemeCardDTO builtin = overview.getThemes().getFirst();
        assertThat(builtin.getPluginCode()).isNull();
        assertThat(builtin.getCode()).isEqualTo("default");
        assertThat(builtin.getActive()).isTrue();
        assertThat(builtin.getEnabled()).isTrue();
        assertThat(overview.getCmsEnabled()).isFalse();
        assertThat(overview.getPresets()).isEmpty();
        assertThat(overview.getEditableThemes()).containsExactly("default");
        org.mockito.Mockito.verifyNoInteractions(cmsPresetAppService);
    }

    @Test
    void overviewMergesRuntimeAndOfflineThemeCards() {
        PluginThemeInfo neco = new PluginThemeInfo("neco-pixel", "neco-pixel", "Neco 像素", "像素风主题",
                Set.of("SITE"), List.of("theme/neco.css"), "preview.png", "home-preset.json", "", "", "", "", "rev-1");
        when(pluginRuntimeGateway.themes()).thenReturn(List.of(neco));
        when(pluginRuntimeGateway.frontendAsset("neco-pixel", "home-preset.json"))
                .thenReturn(Optional.of(new PluginFrontendAssetInfo("home-preset.json", "application/json",
                        "{\"title\":\"像素首页\",\"pages\":[{\"slug\":\"neco-about\",\"title\":\"关于\"}]}"
                                .getBytes(StandardCharsets.UTF_8))));
        PluginModule offline = PluginModule.builder()
                .code("flat-theme").name("扁平主题").description("未启用")
                .themeScopes(List.of("SITE")).build();
        PluginModule enabledModule = PluginModule.builder()
                .code("neco-pixel").name("Neco 像素").themeScopes(List.of("SITE")).build();
        PluginModule plain = PluginModule.builder().code("plain").name("无主题插件").build();
        when(pluginModuleRepo.findAll()).thenReturn(List.of(offline, enabledModule, plain));
        when(pluginThemeAppService.activeThemes()).thenReturn(Map.of("SITE",
                PluginThemeDTO.builder().pluginCode("neco-pixel").build()));
        when(capabilityModuleRepo.findByCode("cms")).thenReturn(Optional.of(enabledCapability()));
        when(cmsPresetAppService.list(null)).thenReturn(List.of(HomePagePresetDTO.builder().code("p1").build()));

        ThemeCenterOverviewDTO overview = service.overview();

        assertThat(overview.getThemes()).hasSize(3);
        ThemeCenterThemeCardDTO builtin = overview.getThemes().get(0);
        assertThat(builtin.getActive()).isFalse();
        ThemeCenterThemeCardDTO necoCard = overview.getThemes().stream()
                .filter(card -> "neco-pixel".equals(card.getPluginCode())).findFirst().orElseThrow();
        assertThat(necoCard.getActive()).isTrue();
        assertThat(necoCard.getEnabled()).isTrue();
        assertThat(necoCard.getPreview()).isEqualTo("preview.png");
        assertThat(necoCard.getHasHomePreset()).isTrue();
        assertThat(necoCard.getHasPageSet()).isTrue();
        ThemeCenterThemeCardDTO offlineCard = overview.getThemes().stream()
                .filter(card -> "flat-theme".equals(card.getPluginCode())).findFirst().orElseThrow();
        assertThat(offlineCard.getEnabled()).isFalse();
        assertThat(offlineCard.getPreview()).isNull();
        assertThat(overview.getCmsEnabled()).isTrue();
        assertThat(overview.getPresets()).hasSize(1);
        assertThat(overview.getEditableThemes()).containsExactly("default", "flat-theme", "neco-pixel");
    }

    @Test
    void overviewEditableThemesIncludesThemesOwningLayouts() {
        when(homePageLayoutRepo.findAll()).thenReturn(List.of(
                HomePageLayout.builder().themeCode("default").build(),
                HomePageLayout.builder().themeCode("retired-theme").build()));

        ThemeCenterOverviewDTO overview = service.overview();

        assertThat(overview.getEditableThemes()).containsExactly("default", "retired-theme");
    }

    @Test
    void overviewCoversEnabledThemeWithoutPersistedScopes() {
        PluginThemeInfo legacy = new PluginThemeInfo("legacy-theme", "legacy-theme", "旧主题", "",
                Set.of("SITE"), List.of("theme.css"), "", "", "", "", "", "", "rev-1");
        when(pluginRuntimeGateway.themes()).thenReturn(List.of(legacy));

        ThemeCenterOverviewDTO overview = service.overview();

        assertThat(overview.getThemes()).hasSize(2);
        ThemeCenterThemeCardDTO legacyCard = overview.getThemes().stream()
                .filter(card -> "legacy-theme".equals(card.getPluginCode())).findFirst().orElseThrow();
        assertThat(legacyCard.getEnabled()).isTrue();
        assertThat(legacyCard.getHasHomePreset()).isFalse();
        assertThat(legacyCard.getHasPageSet()).isFalse();
    }

    @Test
    void activateSiteThemeEnablesPluginDeclaringSiteScope() {
        PluginModule module = PluginModule.builder()
                .code("neco-pixel").themeScopes(List.of("SITE")).build();
        when(pluginModuleRepo.findByCode("neco-pixel")).thenReturn(Optional.of(module));

        service.activateSiteTheme("neco-pixel");

        verify(pluginAppService).enable("neco-pixel");
    }

    @Test
    void activateSiteThemeRejectsPluginWithoutSiteDeclaration() {
        PluginModule module = PluginModule.builder().code("plain").build();
        when(pluginModuleRepo.findByCode("plain")).thenReturn(Optional.of(module));
        when(pluginRuntimeGateway.theme("plain")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activateSiteTheme("plain"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未声明公开站主题");
        verify(pluginAppService, never()).enable(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void activateSiteThemeRejectsUnknownPlugin() {
        when(pluginModuleRepo.findByCode("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activateSiteTheme("ghost"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("插件不存在");
    }

    @Test
    void deactivateSiteThemeDisablesActivePlugin() {
        when(pluginThemeAppService.activeThemes()).thenReturn(Map.of("SITE",
                PluginThemeDTO.builder().pluginCode("neco-pixel").build()));

        service.deactivateSiteTheme();

        verify(pluginAppService).disable("neco-pixel");
    }

    @Test
    void deactivateSiteThemeThrowsWhenNoSiteThemeActive() {
        assertThatThrownBy(() -> service.deactivateSiteTheme())
                .isInstanceOf(BizException.class)
                .hasMessageContaining("当前未启用公开站主题");
        verify(pluginAppService, never()).disable(org.mockito.ArgumentMatchers.anyString());
    }

    private CapabilityModule enabledCapability() {
        CapabilityModule module = new CapabilityModule();
        module.setEnabled(true);
        return module;
    }
}
