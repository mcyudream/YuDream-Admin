package online.yudream.base.application.platform.wiki.service;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.repo.WikiIngestTaskRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiPageVersionRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiReviewItemRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSourceRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.service.WikiIndexGateway;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WikiAppServiceTest {

    private final CapabilityAppService capabilities = mock(CapabilityAppService.class);
    private final WikiSpaceRepo spaces = mock(WikiSpaceRepo.class);
    private final WikiNodeRepo nodes = mock(WikiNodeRepo.class);
    private final WikiPageVersionRepo versions = mock(WikiPageVersionRepo.class);
    private final WikiSourceRepo sources = mock(WikiSourceRepo.class);
    private final WikiIngestTaskRepo ingestTasks = mock(WikiIngestTaskRepo.class);
    private final WikiReviewItemRepo reviewItems = mock(WikiReviewItemRepo.class);
    private final WikiSourceAppService sourceService = mock(WikiSourceAppService.class);
    private final WikiIndexGateway indexes = mock(WikiIndexGateway.class);
    private final WikiAppService service = new WikiAppService(capabilities, spaces, nodes, versions, sources,
            ingestTasks, reviewItems, sourceService, indexes);

    @Test
    void movingDirectoryRewritesDescendantPathsAndPublishedIndexPaths() {
        WikiNode oldParent = directory(1L, null, "old", "/");
        WikiNode target = directory(2L, null, "target", "/");
        WikiNode moved = directory(3L, 1L, "moved", "/1/");
        WikiNode page = WikiNode.page(10L, 3L, "page", "article", 0);
        page.setId(4L);
        page.setAncestorPath("/1/3/");
        page.setPublishedVersionId(20L);
        WikiSpace space = WikiSpace.create("Demo", "demo");
        space.setId(10L);

        when(nodes.findById(3L)).thenReturn(Optional.of(moved));
        when(nodes.findBySpaceId(10L)).thenReturn(List.of(oldParent, target, moved, page));
        when(spaces.findById(10L)).thenReturn(Optional.of(space));
        when(nodes.save(any(WikiNode.class))).thenAnswer(call -> call.getArgument(0));

        service.moveNode(3L, 2L);

        verify(nodes).save(moved);
        verify(nodes).save(page);
        verify(indexes).updatePath(space, 4L, 20L, "target/moved/article");
    }

    private static WikiNode directory(long id, Long parentId, String slug, String ancestorPath) {
        WikiNode node = WikiNode.directory(10L, parentId, slug, slug, 0);
        node.setId(id);
        node.setAncestorPath(ancestorPath);
        return node;
    }
}
