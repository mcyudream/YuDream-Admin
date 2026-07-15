package online.yudream.base.infra.platform.wiki.mapper;

import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.infra.platform.wiki.dataobj.WikiNodeDO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WikiInfraMapperTest {

    @Test
    void preservesOptimisticLockVersionWhenMappingExistingNodeForSave() {
        WikiNodeDO stored = new WikiNodeDO();
        stored.setId(100L);
        stored.setVersion(3);
        stored.setSpaceId(10L);
        stored.setTitle("快速开始");
        stored.setSlug("getting-started");
        stored.setNodeType(WikiNodeType.PAGE);

        WikiNode loaded = WikiInfraMapper.node(stored);
        loaded.saveDraft("快速开始", "# 文档");
        WikiNodeDO saving = WikiInfraMapper.node(loaded);

        assertEquals(100L, saving.getId());
        assertEquals(3, saving.getVersion());
        assertEquals("# 文档", saving.getMarkdownDraft());
    }
}
