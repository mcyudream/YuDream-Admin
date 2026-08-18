package online.yudream.base.application.platform.wiki.service;

import online.yudream.base.application.platform.wiki.assembler.WikiChatActivityAssembler;
import online.yudream.base.application.platform.wiki.dto.WikiChatActivityDTO;
import online.yudream.base.application.platform.wiki.dto.WikiSearchHitDTO;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiPageType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WikiChatAppServiceTest {

    @Test
    void publicSiteKeepsOnlyPublishedPageHits() {
        var published = page(1L, "已发布", "published");
        published.setPublishedVersionId(10L);
        var unpublished = page(2L, "未发布", "unpublished");
        var source = WikiSearchHitDTO.builder()
                .score(1.0)
                .kind("SOURCE")
                .nodeId(null)
                .title("原始资料")
                .path("raw")
                .content("全文")
                .build();

        List<WikiSearchHitDTO> hits = List.of(
                hit("1", "已发布", "published"),
                hit("2", "未发布", "unpublished"),
                source);

        var result = WikiChatAppService.filterVisibleHits(hits, List.of(published, unpublished), true);

        assertThat(result).singleElement().satisfies(item -> assertThat(item.getNodeId()).isEqualTo("1"));
    }

    @Test
    void adminKeepsAllHitsWithoutFiltering() {
        List<WikiSearchHitDTO> hits = List.of(hit("1", "已发布", "published"), hit("2", "未发布", "unpublished"));

        var result = WikiChatAppService.filterVisibleHits(hits, List.of(), false);

        assertThat(result).hasSize(2);
    }

    @Test
    void publicGraphDropsPublishedVersionsOwnedByAnotherNodeOrSpace() {
        WikiNode page = page(1L, "公开页", "public-page");
        page.setPublishedVersionId(10L);
        WikiPageVersion wrongNode = WikiPageVersion.builder()
                .id(10L).spaceId(1L).nodeId(2L).title("其他节点").markdown("正文").build();
        WikiPageVersion wrongSpace = WikiPageVersion.builder()
                .id(10L).spaceId(2L).nodeId(1L).title("其他空间").markdown("正文").build();

        assertThat(WikiChatAppService.materializePublishedPages(List.of(page), List.of(wrongNode), 1L)).isEmpty();
        assertThat(WikiChatAppService.materializePublishedPages(List.of(page), List.of(wrongSpace), 1L)).isEmpty();
    }

    @Test
    void publicGraphSupportsLegacyPublishedMarkdownWithoutFrontmatter() {
        WikiNode page = page(1L, "草稿标题", "public-page");
        page.setPublishedVersionId(10L);
        WikiPageVersion version = WikiPageVersion.builder()
                .id(10L)
                .spaceId(1L)
                .nodeId(1L)
                .title("旧发布标题")
                .markdown("旧版本普通正文")
                .build();

        WikiNode view = WikiChatAppService.materializePublishedPage(page, version);

        assertThat(view).isNotNull();
        assertThat(view.getTitle()).isEqualTo("旧发布标题");
        assertThat(view.bodyMarkdown()).isEqualTo("旧版本普通正文");
    }

    @Test
    void publicGraphKeepsPublishedTitleAfterDraftRename() {
        WikiNode page = page(1L, "草稿新标题", "public-page");
        page.setPublishedVersionId(10L);
        WikiPageVersion version = WikiPageVersion.builder()
                .id(10L)
                .nodeId(1L)
                .title("发布标题")
                .markdown("---\ntitle: 发布标题\ntype: concept\n---\n发布正文")
                .build();

        WikiNode view = WikiChatAppService.materializePublishedPage(page, version);

        assertThat(view.getTitle()).isEqualTo("发布标题");
    }

    @Test
    void publicGraphUsesPublishedVersionRelationsAndIgnoresDraftOnlyLinks() {
        WikiNode page = page(1L, "公开页", "public-page");
        page.setPublishedVersionId(10L);
        page.setMarkdownDraft("---\ntitle: 公开页\ntype: concept\n---\n正文参见 [[秘密页]]");

        WikiPageVersion publishedVersion = WikiPageVersion.builder()
                .id(10L)
                .nodeId(1L)
                .title("公开页")
                .markdown("---\ntitle: 公开页\ntype: concept\nrelated:\n  - 公开邻居\n---\n正文无秘密页链接")
                .build();

        WikiNode publishedNeighbor = page(2L, "公开邻居", "public-neighbor");
        publishedNeighbor.setPublishedVersionId(11L);

        WikiNode secretNeighbor = page(3L, "秘密页", "secret-page");
        secretNeighbor.setPublishedVersionId(12L);

        WikiNode view = WikiChatAppService.materializePublishedPage(page, publishedVersion);
        var graph = WikiChatActivityAssembler.graph(
                "公开页",
                List.of(hit("1", "公开页", "public-page")),
                List.of(view, publishedNeighbor, secretNeighbor));

        assertThat(graph.nodes()).extracting(WikiChatActivityDTO.Node::title)
                .contains("公开邻居")
                .doesNotContain("秘密页");
        assertThat(graph.edges()).extracting(WikiChatActivityDTO.Edge::signal)
                .contains("explicit_related")
                .doesNotContain("direct_link");
    }

    private static WikiNode page(Long id, String title, String slug) {
        WikiNode node = WikiNode.page(1L, null, title, slug, 0);
        node.setId(id);
        node.setNodeType(WikiNodeType.PAGE);
        node.setPageType(WikiPageType.CONCEPT);
        node.setAncestorPath("/");
        return node;
    }

    private static WikiSearchHitDTO hit(String nodeId, String title, String path) {
        return WikiSearchHitDTO.builder()
                .score(0.9)
                .nodeId(nodeId)
                .kind("PAGE")
                .title(title)
                .path(path)
                .content("内容")
                .build();
    }
}
