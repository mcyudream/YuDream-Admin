package online.yudream.base.application.platform.wiki.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSourceRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WikiMigrationAppServiceTest {

    @Test
    void archiveUsesStringIdsAndRestoresParentHierarchy() throws Exception {
        CapabilityAppService capabilities = mock(CapabilityAppService.class);
        WikiSpaceRepo spaces = mock(WikiSpaceRepo.class);
        WikiNodeRepo nodes = mock(WikiNodeRepo.class);
        WikiSourceRepo sources = mock(WikiSourceRepo.class);
        WikiIngestAppService ingest = mock(WikiIngestAppService.class);
        WikiMigrationAppService service = new WikiMigrationAppService(capabilities, spaces, nodes, sources, ingest);

        WikiSpace sourceSpace = WikiSpace.create("Docs", "docs");
        sourceSpace.setId(10L);
        WikiNode directory = WikiNode.directory(10L, null, "指南", "guide", 0);
        directory.setId(9007199254740993L);
        WikiNode page = WikiNode.page(10L, directory.getId(), "开始", "start", 0);
        page.setId(9007199254740995L);
        page.applyGeneratedMarkdown("---\ntitle: 开始\ntype: concept\n---\n正文");
        when(spaces.findById(10L)).thenReturn(Optional.of(sourceSpace));
        when(nodes.findBySpaceId(10L)).thenReturn(List.of(directory, page));
        when(sources.findBySpaceId(10L)).thenReturn(List.of());

        String archive = service.exportArchive(10L);
        JsonNode exported = new ObjectMapper().readTree(archive).path("nodes");
        assertThat(exported.get(0).path("id").isTextual()).isTrue();
        assertThat(exported.get(1).path("parentId").asText()).isEqualTo("9007199254740993");

        WikiSpace importedSpace = WikiSpace.create("Docs", "docs-imported");
        importedSpace.setId(20L);
        when(spaces.findBySlug(any())).thenReturn(Optional.empty());
        when(spaces.save(any())).thenReturn(importedSpace);
        AtomicLong ids = new AtomicLong(100L);
        List<WikiNode> savedNodes = new ArrayList<>();
        when(nodes.save(any())).thenAnswer(invocation -> {
            WikiNode node = invocation.getArgument(0);
            node.setId(ids.incrementAndGet());
            savedNodes.add(node);
            return node;
        });
        when(nodes.findById(any())).thenAnswer(invocation -> savedNodes.stream()
                .filter(node -> node.getId().equals(invocation.getArgument(0)))
                .findFirst());

        assertThat(service.importArchive(archive)).isEqualTo("20");
        assertThat(savedNodes).hasSize(2);
        WikiNode importedDirectory = savedNodes.stream().filter(node -> node.getParentId() == null).findFirst().orElseThrow();
        WikiNode importedPage = savedNodes.stream().filter(node -> node.getParentId() != null).findFirst().orElseThrow();
        assertThat(importedPage.getParentId()).isEqualTo(importedDirectory.getId());
        assertThat(importedPage.getAncestorPath()).contains("/" + importedDirectory.getId() + "/");
    }
}
