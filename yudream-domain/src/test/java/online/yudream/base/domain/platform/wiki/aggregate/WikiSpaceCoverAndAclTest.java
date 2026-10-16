package online.yudream.base.domain.platform.wiki.aggregate;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclMode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceCoverType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceMemberSubjectType;
import online.yudream.base.domain.platform.wiki.valobj.WikiActor;
import online.yudream.base.domain.platform.wiki.valobj.WikiCover;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 空间封面与实例 ACL 数据不变量：旧数据默认无封面/LEGACY，新建 space 显式启用受限；成员 CAS 与超管语义。
 */
class WikiSpaceCoverAndAclTest {

    @Test
    void legacySpaceHasNoCoverAndLegacyAclByDefault() {
        WikiSpace space = WikiSpace.create("演示库", "demo");

        assertEquals(WikiSpaceCoverType.NONE, space.cover().type());
        assertEquals("", space.cover().seed());
        assertNull(space.cover().fileObjectId());
        assertEquals(WikiSpaceAclMode.LEGACY, space.effectiveAclMode());
        assertFalse(space.isCommentEnabled());
    }

    @Test
    void coverValueObjectValidatesPerType() {
        WikiCover seeded = WikiCover.seeded("AbC123", " 封面 ");
        assertEquals(WikiSpaceCoverType.SEEDED, seeded.type());
        assertEquals("abc123", seeded.seed());
        assertEquals("封面", seeded.alt());
        assertNull(seeded.fileObjectId());

        WikiCover upload = WikiCover.upload(42L, "图");
        assertEquals(WikiSpaceCoverType.UPLOAD, upload.type());
        assertEquals(42L, upload.fileObjectId());
        assertEquals("", upload.seed());

        assertThrows(BizException.class, () -> WikiCover.upload(null, "图"));
        assertThrows(BizException.class, () -> WikiCover.seeded("", "图"));
        assertThrows(BizException.class, () -> WikiCover.seeded("bad seed", "图"));
        assertThrows(BizException.class, () -> WikiCover.seeded("x".repeat(65), "图"));
        assertThrows(BizException.class, () -> WikiCover.seeded("ok", "a".repeat(201)));
    }

    @Test
    void noneCoverClearsEveryFieldAndUploadNeverKeepsSeed() {
        WikiSpace space = WikiSpace.create("演示库", "demo");
        space.applyCover(WikiCover.upload(7L, "图"));
        assertEquals(7L, space.cover().fileObjectId());

        space.applyCover(WikiCover.NONE);
        assertEquals(WikiSpaceCoverType.NONE, space.cover().type());
        assertNull(space.cover().fileObjectId());
        assertEquals("", space.cover().seed());
        assertEquals("", space.cover().alt());
    }

    @Test
    void uploadCoverAfterSeededClearsSeedAndViceVersa() {
        WikiSpace space = WikiSpace.create("演示库", "demo");
        space.applyCover(WikiCover.seeded("seed01", "图"));
        space.applyCover(WikiCover.upload(9L, "图"));
        assertEquals("", space.getCoverSeed());
        assertEquals(9L, space.getCoverFileObjectId());

        space.applyCover(WikiCover.seeded("seed02", "图"));
        assertNull(space.getCoverFileObjectId());
        assertEquals("seed02", space.getCoverSeed());
    }

    @Test
    void enableRestrictedOnlyFlipsModeAndCommentToggleIsExplicit() {
        WikiSpace space = WikiSpace.create("演示库", "demo");
        space.enableRestrictedAcl();
        assertEquals(WikiSpaceAclMode.RESTRICTED, space.effectiveAclMode());

        space.applyCommentEnabled(true);
        assertTrue(space.isCommentEnabled());
        space.applyCommentEnabled(false);
        assertFalse(space.isCommentEnabled());
    }

    @Test
    void memberCasRejectsStaleVersionAndKeepsActionSemantics() {
        WikiSpaceMember member = WikiSpaceMember.create(1L, WikiSpaceMemberSubjectType.USER, 100L,
                List.of(WikiSpaceAclAction.READ), 1L);
        member.setVersion(3);

        assertThrows(BizException.class, () -> member.replaceActions(List.of(WikiSpaceAclAction.MANAGE), 2));
        assertEquals(List.of(WikiSpaceAclAction.READ), member.getActions());

        member.replaceActions(List.of(WikiSpaceAclAction.MANAGE), 3);
        assertEquals(List.of(WikiSpaceAclAction.MANAGE), member.getActions());
        assertTrue(member.manages());

        WikiActor actor = WikiActor.of(100L, 7L, "成员", false);
        assertTrue(member.permits(actor, WikiSpaceAclAction.EDIT));
        assertFalse(member.permits(WikiActor.of(101L, 7L, "其他人", false), WikiSpaceAclAction.READ));
        // USER 主体按用户 ID 命中，与当前生效角色无关
        assertTrue(member.permits(WikiActor.of(100L, 8L, "成员", false), WikiSpaceAclAction.READ));
    }

    @Test
    void memberRejectsEmptyActionsAndNullSubject() {
        assertThrows(BizException.class, () -> WikiSpaceMember.create(1L, WikiSpaceMemberSubjectType.USER, 100L,
                List.of(), 1L));
        assertThrows(BizException.class, () -> WikiSpaceMember.create(1L, WikiSpaceMemberSubjectType.USER, null,
                List.of(WikiSpaceAclAction.READ), 1L));
        assertThrows(BizException.class, () -> WikiSpaceMember.create(1L, null, 100L,
                List.of(WikiSpaceAclAction.READ), 1L));
    }
}
