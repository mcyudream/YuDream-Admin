package online.yudream.base.infra.platform.wiki.mapper;

import online.yudream.base.domain.platform.wiki.aggregate.WikiComment;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpaceMember;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCommentStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclMode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceCoverType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceMemberSubjectType;
import online.yudream.base.domain.platform.wiki.valobj.WikiTextAnchor;
import online.yudream.base.domain.platform.wiki.valobj.WikiTextBlockHint;
import online.yudream.base.infra.platform.wiki.dataobj.WikiCommentAnchorDO;
import online.yudream.base.infra.platform.wiki.dataobj.WikiCommentDO;
import online.yudream.base.infra.platform.wiki.dataobj.WikiNodeDO;
import online.yudream.base.infra.platform.wiki.dataobj.WikiSpaceDO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        assertEquals("---\ntitle: \"快速开始\"\ntype: concept\n---\n# 文档\n", saving.getMarkdownDraft());
    }

    @Test
    void legacySpaceWithoutCoverAndAclFieldsFallsBackToSafeDefaults() {
        WikiSpaceDO stored = new WikiSpaceDO();
        stored.setId(1L);
        stored.setName("旧库");
        stored.setSlug("legacy");

        WikiSpace loaded = WikiInfraMapper.toDomain(stored);

        assertEquals(WikiSpaceCoverType.NONE, loaded.getCoverType());
        assertEquals("", loaded.getCoverSeed());
        assertNull(loaded.getCoverFileObjectId());
        assertEquals(WikiSpaceAclMode.LEGACY, loaded.getAclMode());
        assertEquals(WikiSpaceCoverType.NONE, loaded.cover().type());

        // 回写时旧空间的空封面字段落为显式 NONE/LEGACY，不再产生歧义 null。
        WikiSpaceDO saving = WikiInfraMapper.space(loaded);
        assertEquals(WikiSpaceCoverType.NONE, saving.getCoverType());
        assertEquals(WikiSpaceAclMode.LEGACY, saving.getAclMode());
    }

    @Test
    void spaceCoverAndAclRoundTripThroughDataObj() {
        WikiSpace space = WikiSpace.create("新库", "restricted");
        space.setId(2L);
        space.applyCover(online.yudream.base.domain.platform.wiki.valobj.WikiCover.seeded("seed01", "封面"));
        space.enableRestrictedAcl();
        space.applyCommentEnabled(true);

        WikiSpace loaded = WikiInfraMapper.toDomain(WikiInfraMapper.space(space));

        assertEquals(WikiSpaceCoverType.SEEDED, loaded.getCoverType());
        assertEquals("seed01", loaded.getCoverSeed());
        assertEquals("封面", loaded.getCoverAlt());
        assertEquals(WikiSpaceAclMode.RESTRICTED, loaded.getAclMode());
        assertEquals(true, loaded.isCommentEnabled());
    }

    @Test
    void memberAndCommentRoundTripThroughDataObj() {
        WikiSpaceMember member = WikiSpaceMember.create(1L, WikiSpaceMemberSubjectType.ROLE, 7L,
                List.of(WikiSpaceAclAction.READ, WikiSpaceAclAction.COMMENT), 5L);
        member.setId(11L);
        member.setVersion(2);

        WikiSpaceMember memberLoaded = WikiInfraMapper.member(WikiInfraMapper.member(member));

        assertEquals(11L, memberLoaded.getId());
        assertEquals(2, memberLoaded.getVersion());
        assertEquals(WikiSpaceMemberSubjectType.ROLE, memberLoaded.getSubjectType());
        assertEquals(7L, memberLoaded.getSubjectId());
        assertEquals(List.of(WikiSpaceAclAction.READ, WikiSpaceAclAction.COMMENT), memberLoaded.getActions());

        WikiComment comment = WikiComment.create(1L, 20L, null, 100L, "小明", "内容");
        comment.setId(12L);
        comment.publish(200L);

        WikiComment commentLoaded = WikiInfraMapper.comment(WikiInfraMapper.comment(comment));

        assertEquals(12L, commentLoaded.getId());
        assertEquals(WikiCommentStatus.PUBLISHED, commentLoaded.getStatus());
        assertEquals(100L, commentLoaded.getAuthorUserId());
        assertEquals("小明", commentLoaded.getAuthorName());
        assertEquals("内容", commentLoaded.getContent());
        assertEquals(200L, commentLoaded.getReviewedByUserId());
        assertNull(commentLoaded.getAnchor());
    }

    @Test
    void commentAnchorRoundTripsThroughDataObjWithServerStamp() {
        WikiTextAnchor anchor = WikiTextAnchor.stamp(WikiTextAnchor.draft("快速开始", "欢迎来到", "页面。",
                        WikiTextBlockHint.of("tableCell", 4, 1, 2), 3, 8),
                200L, 3L, "hash-1", "欢迎来到快速开始页面。");
        WikiComment comment = WikiComment.create(1L, 20L, null, 100L, "小明", "内容", anchor);
        comment.setId(12L);

        WikiComment loaded = WikiInfraMapper.comment(WikiInfraMapper.comment(comment));

        assertNotNull(loaded.getAnchor());
        assertEquals("快速开始", loaded.getAnchor().exact());
        assertEquals("欢迎来到", loaded.getAnchor().prefix());
        assertEquals("页面。", loaded.getAnchor().suffix());
        assertEquals("tableCell", loaded.getAnchor().blockHint().type().wireValue());
        assertEquals(4, loaded.getAnchor().blockHint().ordinal());
        assertEquals(1, loaded.getAnchor().blockHint().tableRow());
        assertEquals(2, loaded.getAnchor().blockHint().tableCell());
        assertEquals(3, loaded.getAnchor().startOffset());
        assertEquals(8, loaded.getAnchor().endOffset());
        assertEquals(200L, loaded.getAnchor().versionId());
        assertEquals(3L, loaded.getAnchor().revision());
        assertEquals("hash-1", loaded.getAnchor().contentHash());
    }

    @Test
    void legacyCommentWithoutAnchorStaysUnanchored() {
        WikiCommentDO stored = new WikiCommentDO();
        stored.setId(13L);
        stored.setSpaceId(1L);
        stored.setNodeId(20L);
        stored.setContent("旧评论");

        assertNull(WikiInfraMapper.comment(stored).getAnchor());

        WikiComment unanchored = WikiComment.create(1L, 20L, null, 100L, "小明", "内容");
        assertNull(WikiInfraMapper.comment(unanchored).getAnchor());
    }

    @Test
    void incompleteStoredAnchorIsDroppedWithoutBlockingCommentRead() {
        WikiCommentAnchorDO corrupted = new WikiCommentAnchorDO();
        corrupted.setExact("快速开始");
        corrupted.setRevision(3L);
        WikiCommentDO stored = new WikiCommentDO();
        stored.setId(14L);
        stored.setContent("评论");
        stored.setAnchor(corrupted);

        WikiComment loaded = WikiInfraMapper.comment(stored);

        assertNull(loaded.getAnchor());
        assertEquals("评论", loaded.getContent());
    }

    @Test
    void unknownStoredBlockTypeDropsHintButKeepsQuote() {
        WikiCommentAnchorDO storedAnchor = new WikiCommentAnchorDO();
        storedAnchor.setExact("快速开始");
        storedAnchor.setPrefix("欢迎来到");
        storedAnchor.setBlockType("LEGACY_UNKNOWN");
        storedAnchor.setBlockOrdinal(2);
        storedAnchor.setTableRow(1);
        storedAnchor.setVersionId(200L);
        storedAnchor.setRevision(1L);
        storedAnchor.setContentHash("hash-1");
        WikiCommentDO stored = new WikiCommentDO();
        stored.setId(15L);
        stored.setAnchor(storedAnchor);

        WikiTextAnchor loaded = WikiInfraMapper.comment(stored).getAnchor();

        assertNotNull(loaded);
        assertEquals("快速开始", loaded.exact());
        assertEquals("欢迎来到", loaded.prefix());
        assertNull(loaded.blockHint());
        assertEquals(200L, loaded.versionId());
        assertEquals("hash-1", loaded.contentHash());
    }
}
