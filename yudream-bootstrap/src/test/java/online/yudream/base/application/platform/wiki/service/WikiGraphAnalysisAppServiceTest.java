package online.yudream.base.application.platform.wiki.service;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.wiki.dto.WikiGraphSnapshotDTO;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiPageType;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WikiGraphAnalysisAppServiceTest {

    private final CapabilityAppService capabilities = mock(CapabilityAppService.class);
    private final WikiSpaceRepo spaces = mock(WikiSpaceRepo.class);
    private final WikiNodeRepo nodes = mock(WikiNodeRepo.class);

    private final WikiGraphAnalysisAppService service =
            new WikiGraphAnalysisAppService(capabilities, spaces, nodes);

    @Test
    void adamicAdarEdgesAreIndependentOfPageOrder() {
        WikiNode a = page(1L, "A", "[[B]]", WikiPageType.ENTITY);
        WikiNode b = page(2L, "B", "[[C]]", WikiPageType.CONCEPT);
        WikiNode c = page(3L, "C", "[[D]]", WikiPageType.SOURCE_SUMMARY);
        WikiNode d = page(4L, "D", "", WikiPageType.SYNTHESIS);

        var forward = snapshot(List.of(a, b, c, d));
        var shuffled = snapshot(List.of(b, a, d, c));

        assertThat(adamicAdarKeys(forward)).hasSize(2);
        assertThat(adamicAdarKeys(shuffled)).containsExactlyElementsOf(adamicAdarKeys(forward));
    }

    private WikiGraphSnapshotDTO snapshot(List<WikiNode> pages) {
        when(spaces.findById(anyLong())).thenReturn(Optional.of(WikiSpace.create("Demo", "demo")));
        when(nodes.findBySpaceId(anyLong())).thenReturn(pages);
        return service.snapshot(100L);
    }

    private static List<String> adamicAdarKeys(WikiGraphSnapshotDTO snapshot) {
        return snapshot.edges().stream()
                .filter(edge -> "adamic_adar".equals(edge.signal()))
                .map(edge -> edge.source() + "->" + edge.target() + "=" + edge.weight())
                .sorted()
                .toList();
    }

    private static WikiNode page(Long id, String title, String body, WikiPageType type) {
        WikiNode node = WikiNode.page(100L, null, title, title.toLowerCase(), 0);
        node.setId(id);
        node.setPageType(type);
        node.setMarkdownDraft("---\ntitle: " + title + "\ntype: " + type.name().toLowerCase() + "\n---\n" + body);
        return node;
    }
}
