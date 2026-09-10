package online.yudream.base.application.platform.cms;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.cms.dto.CmsTemplateContextDTO;
import online.yudream.base.application.platform.cms.query.CmsTemplateContextQuery;
import online.yudream.base.application.platform.cms.service.CmsTemplateContextAppService;
import online.yudream.base.application.platform.theme.service.SiteThemeQueryService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.cms.aggregate.CmsPage;
import online.yudream.base.domain.platform.cms.enumerate.PageStatus;
import online.yudream.base.domain.platform.cms.repo.CmsPageRepo;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiPageVersionRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.plugin.spi.system.extension.PluginExtensionQuery;
import online.yudream.base.plugin.spi.theme.PluginThemeBlockProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CmsTemplateContextAppServiceTest {

    @Mock
    private CapabilityAppService capabilities;
    @Mock
    private CmsPageRepo cmsPages;
    @Mock
    private WikiSpaceRepo wikiSpaces;
    @Mock
    private WikiNodeRepo wikiNodes;
    @Mock
    private WikiPageVersionRepo wikiVersions;
    @Mock
    private SiteThemeQueryService siteThemeQueryService;
    @Mock
    private PluginExtensionQuery pluginExtensionQuery;

    private CmsTemplateContextAppService service;

    @BeforeEach
    void setUp() {
        service = new CmsTemplateContextAppService(capabilities, cmsPages, wikiSpaces, wikiNodes, wikiVersions,
                siteThemeQueryService, pluginExtensionQuery, new ObjectMapper());
        lenient().when(siteThemeQueryService.activeSiteThemeCode()).thenReturn("default");
    }

    @Test
    void exposesOnlyPublishedCmsPages() {
        CmsPage published = CmsPage.builder()
                .id(1L)
                .title("Published")
                .slug("published")
                .summary("summary")
                .status(PageStatus.PUBLISHED)
                .publishedAt(LocalDateTime.now())
                .build();
        when(cmsPages.publishedPage(eq("default"), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(published), 1, 1, 12));
        when(capabilities.enabled("wiki")).thenReturn(false);

        CmsTemplateContextDTO context = service.query();

        assertThat(context.getCms().getPages().getLatest())
                .extracting(item -> item.getSlug())
                .containsExactly("published");
        assertThat(context.getKnowledge().getPages()).isEmpty();
    }

    @Test
    void queriesCmsPagesOfTheActiveSiteThemeOnly() {
        when(siteThemeQueryService.activeSiteThemeCode()).thenReturn("neco");
        when(cmsPages.publishedPage(eq("neco"), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(PageResult.empty(1, 12));
        when(capabilities.enabled("wiki")).thenReturn(false);

        service.query();

        org.mockito.Mockito.verify(cmsPages)
                .publishedPage(eq("neco"), isNull(), isNull(), isNull(), anyInt(), anyInt());
    }

    @Test
    void passesTemplateListLimitsToTheDataLayer() {
        CmsPage first = CmsPage.builder().id(1L).title("One").slug("one").status(PageStatus.PUBLISHED).build();
        CmsPage second = CmsPage.builder().id(2L).title("Two").slug("two").status(PageStatus.PUBLISHED).build();
        when(cmsPages.publishedPage(eq("default"), isNull(), isNull(), isNull(), eq(1), eq(2)))
                .thenReturn(new PageResult<>(List.of(first, second), 1, 2, 2));
        when(capabilities.enabled("wiki")).thenReturn(false);
        CmsTemplateContextQuery query = new CmsTemplateContextQuery();
        query.setCmsLatestLimit(2);

        CmsTemplateContextDTO context = service.query(query);

        assertThat(context.getCms().getPages().getLatest()).hasSize(2);
        org.mockito.Mockito.verify(cmsPages).publishedPage(eq("default"), isNull(), isNull(), isNull(), eq(1), eq(2));
    }

    @Test
    void exposesTheCurrentPublishedWikiVersionOnlyForPublicSpaces() {
        WikiSpace space = WikiSpace.builder().id(10L).name("Docs").slug("docs").publicReadEnabled(true).build();
        WikiSpace privateSpace = WikiSpace.builder().id(11L).name("Private").slug("private").publicReadEnabled(false).build();
        WikiNode node = WikiNode.builder()
                .id(20L).spaceId(10L).title("Install").slug("install")
                .nodeType(WikiNodeType.PAGE).publishedVersionId(30L).build();
        WikiPageVersion published = WikiPageVersion.builder()
                .id(30L).nodeId(20L).spaceId(10L).revision(2).title("Install")
                .markdown("published markdown").build();
        when(cmsPages.publishedPage(eq("default"), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(PageResult.empty(1, 12));
        when(capabilities.enabled("wiki")).thenReturn(true);
        when(wikiSpaces.findAll()).thenReturn(List.of(space, privateSpace));
        when(wikiNodes.findBySpaceId(10L)).thenReturn(List.of(node));
        when(wikiVersions.findById(30L)).thenReturn(Optional.of(published));

        CmsTemplateContextDTO context = service.query();

        assertThat(context.getKnowledge().getPages()).hasSize(1);
        assertThat(context.getKnowledge().getPages().get(0).getContent()).isEqualTo("published markdown");
        assertThat(context.getKnowledge().getPages().get(0).getUrl()).isEqualTo("/wiki/docs/install");
        assertThat(context.getKnowledge().getSpaces()).extracting(item -> item.getSlug()).containsExactly("docs");
    }
    @Test
    void resolvesRequestedThemeBlocksForTheActiveTheme() {
        when(cmsPages.publishedPage(eq("default"), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(PageResult.empty(1, 12));
        when(capabilities.enabled("wiki")).thenReturn(false);
        PluginThemeBlockProvider provider = new PluginThemeBlockProvider() {
            @Override
            public String code() {
                return "timeline";
            }

            @Override
            public String name() {
                return "大事记";
            }

            @Override
            public Object data(online.yudream.base.plugin.spi.theme.PluginThemeBlockContext ctx) {
                return List.of(java.util.Map.of("title", "一周年", "limit", ctx.limit()));
            }
        };
        when(pluginExtensionQuery.extensions(PluginThemeBlockProvider.class)).thenReturn(List.of(provider));
        CmsTemplateContextQuery query = new CmsTemplateContextQuery();
        query.setBlocks(List.of("timeline", "server-list"));
        query.setBlockLimit(5);

        CmsTemplateContextDTO context = service.query(query);

        assertThat(context.getBlocks()).containsOnlyKeys("timeline");
        assertThat(context.getBlocks().get("timeline"))
                .isEqualTo(List.of(java.util.Map.of("title", "一周年", "limit", 5)));
    }

    @Test
    void skipsBlocksNotSupportingTheActiveTheme() {
        when(cmsPages.publishedPage(eq("default"), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(PageResult.empty(1, 12));
        when(capabilities.enabled("wiki")).thenReturn(false);
        PluginThemeBlockProvider provider = new PluginThemeBlockProvider() {
            @Override
            public String code() {
                return "server-list";
            }

            @Override
            public String name() {
                return "服务器";
            }

            @Override
            public java.util.Set<String> supportedThemes() {
                return java.util.Set.of("neco");
            }

            @Override
            public Object data(online.yudream.base.plugin.spi.theme.PluginThemeBlockContext ctx) {
                return List.of();
            }
        };
        when(pluginExtensionQuery.extensions(PluginThemeBlockProvider.class)).thenReturn(List.of(provider));
        CmsTemplateContextQuery query = new CmsTemplateContextQuery();
        query.setBlocks(List.of("server-list"));

        CmsTemplateContextDTO context = service.query(query);

        assertThat(context.getBlocks()).isEmpty();
    }

    @Test
    void isolatesFailingBlockProviders() {
        when(cmsPages.publishedPage(eq("default"), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(PageResult.empty(1, 12));
        when(capabilities.enabled("wiki")).thenReturn(false);
        PluginThemeBlockProvider broken = new PluginThemeBlockProvider() {
            @Override
            public String code() {
                return "timeline";
            }

            @Override
            public String name() {
                return "大事记";
            }

            @Override
            public Object data(online.yudream.base.plugin.spi.theme.PluginThemeBlockContext ctx) {
                throw new IllegalStateException("boom");
            }
        };
        when(pluginExtensionQuery.extensions(PluginThemeBlockProvider.class)).thenReturn(List.of(broken));
        CmsTemplateContextQuery query = new CmsTemplateContextQuery();
        query.setBlocks(List.of("timeline"));

        CmsTemplateContextDTO context = service.query(query);

        assertThat(context.getBlocks()).isEmpty();
        assertThat(context.getCms()).isNotNull();
    }

    @Test
    void doesNotTouchBlockProvidersWhenTemplateRequestsNoBlocks() {
        when(cmsPages.publishedPage(eq("default"), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(PageResult.empty(1, 12));
        when(capabilities.enabled("wiki")).thenReturn(false);

        CmsTemplateContextDTO context = service.query();

        assertThat(context.getBlocks()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(pluginExtensionQuery);
    }
}
