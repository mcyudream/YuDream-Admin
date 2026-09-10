package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.service.AgentRuntimeApplicationRegistry;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.graph.PluginGraphService;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryService;
import online.yudream.base.plugin.spi.theme.PluginTheme;
import online.yudream.base.plugin.spi.theme.PluginThemeScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class PluginContextImplThemeTest {

    @Mock
    private FrameworkServices frameworkServices;

    @Mock
    private PluginServiceRegistry pluginServiceRegistry;

    @Mock
    private PluginAiToolRegistry aiToolRegistry;

    @Mock
    private PluginGraphService graphService;

    @Mock
    private PluginSemanticMemoryService semanticMemoryService;

    @Mock
    private AgentRuntimeApplicationRegistry agentApplicationRegistry;

    @Mock
    private PluginExtensionRegistry extensionRegistry;

    private PluginContextImpl context;

    @BeforeEach
    void setUp() {
        context = new PluginContextImpl(
                "theme-plugin",
                null,
                frameworkServices,
                pluginServiceRegistry,
                Set.of(),
                code -> true,
                aiToolRegistry,
                graphService,
                semanticMemoryService,
                agentApplicationRegistry,
                (code, visible) -> {
                },
                extensionRegistry
        );
    }

    @Test
    void registerThemeExposesThemeUntilRuntimeContributionsCleared() {
        context.registerTheme(theme(Set.of(PluginThemeScope.SITE)));

        assertThat(context.theme()).hasValueSatisfying(theme -> {
            assertThat(theme.code()).isEqualTo("neco");
            assertThat(theme.scopes()).containsExactly(PluginThemeScope.SITE);
        });

        context.clearRuntimeContributions();

        assertThat(context.theme()).isEmpty();
    }

    @Test
    void duplicateThemeRegistrationIsRejected() {
        context.registerTheme(theme(Set.of(PluginThemeScope.SITE)));

        assertThatThrownBy(() -> context.registerTheme(theme(Set.of(PluginThemeScope.ADMIN))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("一个插件最多注册一个主题");
    }

    @Test
    void themeRequiresScopeAndStyle() {
        assertThatThrownBy(() -> context.registerTheme(new PluginTheme(
                "neco", "Neco 主题", "", Set.of(), List.of("theme/neco.css"), "", "", "", "", "")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("至少声明一个生效范围");
        assertThatThrownBy(() -> context.registerTheme(new PluginTheme(
                "neco", "Neco 主题", "", Set.of(PluginThemeScope.SITE), List.of(), "", "", "", "", "")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("至少声明一个样式资产");
    }

    @Test
    void themeRejectsIllegalAssetPath() {
        assertThatThrownBy(() -> context.registerTheme(new PluginTheme(
                "neco", "Neco 主题", "", Set.of(PluginThemeScope.SITE), List.of("../escape.css"), "", "", "", "", "")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("样式路径非法");
        assertThatThrownBy(() -> context.registerTheme(new PluginTheme(
                "neco", "Neco 主题", "", Set.of(PluginThemeScope.SITE), List.of("theme/neco.css"), "/abs/preview.png", "", "", "", "")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("预览图路径非法");
    }

    @Test
    void themeValidatesHomeComponent() {
        context.registerTheme(new PluginTheme("neco", "Neco 主题", "", Set.of(PluginThemeScope.SITE),
                List.of("theme/neco.css"), "", "", "", "theme/Home", ""));
        assertThat(context.theme()).hasValueSatisfying(theme ->
                assertThat(theme.homeComponent()).isEqualTo("theme/Home"));

        // 同一上下文只允许一个主题，非法用例注册前先清场
        context.clearRuntimeContributions();
        assertThatThrownBy(() -> context.registerTheme(new PluginTheme(
                "neco2", "Neco2", "", Set.of(PluginThemeScope.SITE), List.of("theme/neco.css"), "", "", "", "Home", "")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("首页组件名非法");
        assertThatThrownBy(() -> context.registerTheme(new PluginTheme(
                "neco3", "Neco3", "", Set.of(PluginThemeScope.SITE), List.of("theme/neco.css"), "", "", "", "../theme/Home", "")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("首页组件名非法");
    }

    @Test
    void themeValidatesChromeComponent() {
        context.registerTheme(new PluginTheme("neco", "Neco 主题", "", Set.of(PluginThemeScope.SITE),
                List.of("theme/neco.css"), "", "", "", "", "theme/Chrome"));
        assertThat(context.theme()).hasValueSatisfying(theme ->
                assertThat(theme.chromeComponent()).isEqualTo("theme/Chrome"));

        context.clearRuntimeContributions();
        assertThatThrownBy(() -> context.registerTheme(new PluginTheme(
                "neco2", "Neco2", "", Set.of(PluginThemeScope.SITE), List.of("theme/neco.css"), "", "", "", "", "Chrome")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("chrome组件名非法");
    }

    private PluginTheme theme(Set<PluginThemeScope> scopes) {
        return new PluginTheme("neco", "Neco 主题", "像素风主题", scopes,
                List.of("theme/neco.css"), "theme/preview.png", "", "", "", "");
    }
}
