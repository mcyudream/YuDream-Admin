package online.yudream.base.application.platform.wiki.assembler;

import online.yudream.base.application.platform.wiki.dto.WikiChatActivityDTO;
import online.yudream.base.application.platform.wiki.dto.WikiSearchHitDTO;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiPageType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WikiChatActivityAssemblerTest {

    @Test
    void graphContainsQueryNodeAndRetrievalMatchEdgesOnly() {
        var a = page(1L, "虚拟线程", "virtual-thread");
        var b = page(2L, "并发控制", "concurrency-control");
        List<WikiSearchHitDTO> hits = List.of(
                hit(0.95, "1", "虚拟线程", "virtual-thread", "内容A"),
                hit(0.8, "2", "并发控制", "concurrency-control", "内容B"));

        var graph = WikiChatActivityAssembler.graph("虚拟线程", hits, List.of(a, b));

        assertThat(graph.query()).isEqualTo("虚拟线程");
        assertThat(graph.nodes()).extracting(WikiChatActivityDTO.Node::id)
                .containsExactly("query", "1", "2");
        assertThat(graph.nodes().getFirst().role()).isEqualTo("query");
        assertThat(graph.edges()).extracting(WikiChatActivityDTO.Edge::signal)
                .containsExactly("retrieval_match", "retrieval_match");
    }

    @Test
    void graphDiscoversRealRelationSignalsBetweenHitPages() {
        var a = page(1L, "虚拟线程", "virtual-thread");
        a.setRelated(List.of("并发控制"));
        a.setSources(List.of("source-a"));
        a.setMarkdownDraft("---\ntitle: 虚拟线程\ntype: concept\n---\n参见 [[并发控制]]");
        var b = page(2L, "并发控制", "concurrency-control");
        b.setSources(List.of("source-a"));
        List<WikiSearchHitDTO> hits = List.of(
                hit(0.95, "1", "虚拟线程", "virtual-thread", "内容A"),
                hit(0.8, "2", "并发控制", "concurrency-control", "内容B"));

        var graph = WikiChatActivityAssembler.graph("虚拟线程", hits, List.of(a, b));

        assertThat(graph.edges()).extracting(WikiChatActivityDTO.Edge::signal)
                .contains("explicit_related", "direct_link", "source_overlap");
    }

    @Test
    void graphExpandsAtMostOneNeighborLayerAndRespectsNodeCap() {
        var a = page(1L, "虚拟线程", "virtual-thread");
        a.setRelated(List.of("邻居页"));
        var b = page(2L, "并发控制", "concurrency-control");
        var neighbor = page(3L, "邻居页", "neighbor");
        List<WikiSearchHitDTO> hits = List.of(hit(0.95, "1", "虚拟线程", "virtual-thread", "内容A"));

        var graph = WikiChatActivityAssembler.graph("虚拟线程", hits, List.of(a, b, neighbor));

        assertThat(graph.nodes()).extracting(WikiChatActivityDTO.Node::id)
                .containsExactly("query", "1", "3");
        assertThat(graph.edges()).extracting(WikiChatActivityDTO.Edge::signal)
                .contains("explicit_related");
        assertThat(graph.nodes()).hasSizeLessThanOrEqualTo(24);
        assertThat(graph.edges()).hasSizeLessThanOrEqualTo(48);
    }

    @Test
    void hitsFiltersOutUnpublishedPagesInPublicScenario() {
        var published = page(1L, "已发布", "published");
        published.setPublishedVersionId(10L);
        var unpublished = page(2L, "未发布", "unpublished");
        List<WikiSearchHitDTO> hits = List.of(
                hit(0.9, "1", "已发布", "published", "内容A"),
                hit(0.8, "2", "未发布", "unpublished", "内容B"));

        List<WikiChatActivityDTO.Hit> result = WikiChatActivityAssembler.hits(hits, List.of(published));

        assertThat(result).singleElement().satisfies(item ->
                assertThat(item.nodeId()).isEqualTo("1"));
    }

    private static WikiNode page(Long id, String title, String slug) {
        WikiNode node = WikiNode.page(1L, null, title, slug, 0);
        node.setId(id);
        node.setNodeType(WikiNodeType.PAGE);
        node.setPageType(WikiPageType.CONCEPT);
        node.setAncestorPath("/");
        node.setMarkdownDraft("---\ntitle: " + title + "\ntype: concept\n---\n正文");
        return node;
    }

    private static WikiSearchHitDTO hit(double score, String nodeId, String title, String path, String content) {
        return WikiSearchHitDTO.builder()
                .score(score)
                .nodeId(nodeId)
                .kind("PAGE")
                .title(title)
                .path(path)
                .content(content)
                .build();
    }
}
