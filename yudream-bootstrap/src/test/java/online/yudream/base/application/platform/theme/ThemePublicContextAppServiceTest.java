package online.yudream.base.application.platform.theme;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.theme.dto.ThemePublicContextDTO;
import online.yudream.base.application.platform.theme.query.ThemePublicContextQuery;
import online.yudream.base.application.platform.theme.service.SiteThemeQueryService;
import online.yudream.base.application.platform.theme.service.ThemeBlockAppService;
import online.yudream.base.application.platform.theme.service.ThemeConfigAppService;
import online.yudream.base.application.platform.theme.service.ThemePublicContextAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.cms.aggregate.CmsPage;
import online.yudream.base.domain.platform.cms.repo.CmsPageRepo;
import online.yudream.base.plugin.spi.system.extension.PluginExtensionQuery;
import online.yudream.base.plugin.spi.theme.PluginThemeBlockContext;
import online.yudream.base.plugin.spi.theme.PluginThemeBlockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThemePublicContextAppServiceTest {

    @Mock
    private SiteThemeQueryService siteThemeQueryService;

    @Mock
    private ThemeConfigAppService themeConfigAppService;

    @Mock
    private CapabilityAppService capabilities;

    @Mock
    private CmsPageRepo cmsPages;

    @Mock
    private PluginExtensionQuery pluginExtensionQuery;

    private ThemePublicContextAppService service;

    @BeforeEach
    void setUp() {
        ThemeBlockAppService blockAppService = new ThemeBlockAppService(pluginExtensionQuery, new ObjectMapper());
        service = new ThemePublicContextAppService(
                siteThemeQueryService, themeConfigAppService, blockAppService, capabilities, cmsPages);
        lenient().when(siteThemeQueryService.activeSiteThemeCode()).thenReturn("neco-pixel");
        lenient().when(themeConfigAppService.publicConfig("neco-pixel"))
                .thenReturn(Map.of("heroTitle", "南京大学Minecraft协会"));
        lenient().when(capabilities.enabled("cms")).thenReturn(true);
    }

    @Test
    void contextResolvesRequestedBlocksWithThemeFilterAndIsolation() {
        when(pluginExtensionQuery.extensions(PluginThemeBlockProvider.class)).thenReturn(List.of(
                new StubProvider("server-list", Set.of("neco-pixel"), ctx -> Map.of("count", 2)),
                new StubProvider("legacy-only", Set.of("other-theme"), ctx -> Map.of("count", 9)),
                new StubProvider("broken", Set.of(), ctx -> {
                    throw new IllegalStateException("boom");
                })
        ));

        ThemePublicContextQuery query = new ThemePublicContextQuery();
        query.setBlocks("server-list,legacy-only,broken,../evil");
        query.setCmsLatest(0);

        ThemePublicContextDTO context = service.context(query);

        assertThat(context.getThemeCode()).isEqualTo("neco-pixel");
        assertThat(context.getThemeConfig()).containsEntry("heroTitle", "南京大学Minecraft协会");
        assertThat(context.getBlocks()).containsOnlyKeys("server-list");
        assertThat(context.getBlocks().get("server-list")).isEqualTo(Map.of("count", 2));
        assertThat(context.getCmsPagesLatest()).isEmpty();
    }

    @Test
    void contextReturnsLatestCmsPagesWithinLimit() {
        CmsPage older = page(1L, "旧闻", "old", LocalDateTime.of(2026, 1, 1, 0, 0));
        CmsPage newer = page(2L, "新闻", "new", LocalDateTime.of(2026, 2, 1, 0, 0));
        when(cmsPages.publishedPage(anyString(), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(older, newer), 2, 1, 10));

        ThemePublicContextQuery query = new ThemePublicContextQuery();
        query.setCmsLatest(5);

        ThemePublicContextDTO context = service.context(query);

        assertThat(context.getCmsPagesLatest()).extracting("slug").containsExactly("new", "old");
        assertThat(context.getCmsPagesLatest().get(0).getUrl()).isEqualTo("/site/new");
    }

    @Test
    void contextDegradesWhenCmsCapabilityDisabled() {
        when(capabilities.enabled("cms")).thenReturn(false);

        ThemePublicContextQuery query = new ThemePublicContextQuery();
        query.setCmsLatest(5);

        assertThat(service.context(query).getCmsPagesLatest()).isEmpty();
    }

    private CmsPage page(Long id, String title, String slug, LocalDateTime publishedAt) {
        return CmsPage.builder()
                .id(id)
                .title(title)
                .slug(slug)
                .publishedAt(publishedAt)
                .build();
    }

    private record StubProvider(String code, Set<String> themes,
                                java.util.function.Function<PluginThemeBlockContext, Object> dataFn)
            implements PluginThemeBlockProvider {

        @Override
        public String name() {
            return code;
        }

        @Override
        public Set<String> supportedThemes() {
            return themes;
        }

        @Override
        public Object data(PluginThemeBlockContext ctx) {
            return dataFn.apply(ctx);
        }
    }
}
