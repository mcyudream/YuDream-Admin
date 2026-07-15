package online.yudream.base.application.platform.cms;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.cms.dto.CmsTemplateContextDTO;
import online.yudream.base.application.platform.cms.service.CmsTemplateContextAppService;
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
import static org.mockito.ArgumentMatchers.isNull;
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

    private CmsTemplateContextAppService service;

    @BeforeEach
    void setUp() {
        service = new CmsTemplateContextAppService(capabilities, cmsPages, wikiSpaces, wikiNodes, wikiVersions);
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
        when(cmsPages.publishedPage(isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(published), 1, 1, 12));
        when(capabilities.enabled("wiki")).thenReturn(false);

        CmsTemplateContextDTO context = service.query();

        assertThat(context.getCms().getPages().getLatest())
                .extracting(item -> item.getSlug())
                .containsExactly("published");
        assertThat(context.getKnowledge().getPages()).isEmpty();
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
        when(cmsPages.publishedPage(isNull(), isNull(), isNull(), anyInt(), anyInt()))
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
}
