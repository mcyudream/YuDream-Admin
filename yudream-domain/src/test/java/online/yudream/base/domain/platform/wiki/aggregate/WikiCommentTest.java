package online.yudream.base.domain.platform.wiki.aggregate;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCommentAiVerdict;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCommentReviewMode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCommentRagStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCommentStatus;
import online.yudream.base.domain.platform.wiki.valobj.WikiTextAnchor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 评论状态机：软隐藏/软删除可恢复、作者本人校验、纯文本限长、检索候选默认关闭、文本锚点继承规则。
 */
class WikiCommentTest {

    private static final long AUTHOR = 100L;
    private static final long REVIEWER = 200L;

    @Test
    void newCommentStartsPendingAndOutOfRetrieval() {
        WikiComment comment = WikiComment.create(1L, 2L, null, AUTHOR, "小明", "  第一行  \r\n第二行  ");

        assertEquals(WikiCommentStatus.PENDING, comment.getStatus());
        assertEquals(WikiCommentRagStatus.DISABLED, comment.getRagStatus());
        assertFalse(comment.isPinned());
        // 纯文本正文只统一换行符并裁剪首尾空白，保留行内空格
        assertEquals("第一行  \n第二行", comment.getContent());
        assertEquals(WikiCommentStatus.PENDING, comment.getStatus());
        assertFalse(comment.visibleToPublic());
    }

    @Test
    void contentIsPlainTextAndLengthLimited() {
        assertThrows(BizException.class, () -> WikiComment.create(1L, 2L, null, AUTHOR, "小明", "   "));
        assertThrows(BizException.class, () -> WikiComment.create(1L, 2L, null, AUTHOR, "小明",
                "x".repeat(WikiComment.MAX_CONTENT_LENGTH + 1)));
        assertEquals("ab", WikiComment.create(1L, 2L, null, AUTHOR, "小明", "a\u0000b").getContent());
    }

    @Test
    void publishingEnablesRetrievalCandidateAndHidingDisablesIt() {
        WikiComment comment = WikiComment.create(1L, 2L, null, AUTHOR, "小明", "内容");

        comment.publish(REVIEWER);
        assertEquals(WikiCommentStatus.PUBLISHED, comment.getStatus());
        assertEquals(WikiCommentRagStatus.PENDING, comment.getRagStatus());
        assertEquals(REVIEWER, comment.getReviewedByUserId());
        assertTrue(comment.visibleToPublic());

        comment.hide(REVIEWER);
        assertEquals(WikiCommentStatus.HIDDEN, comment.getStatus());
        assertEquals(WikiCommentRagStatus.DISABLED, comment.getRagStatus());
        assertFalse(comment.visibleToPublic());

        comment.restore(REVIEWER);
        assertEquals(WikiCommentStatus.PUBLISHED, comment.getStatus());
        assertEquals(WikiCommentRagStatus.PENDING, comment.getRagStatus());
    }

    @Test
    void softDeleteIsRecoverableAndRecoveredCommentIsPublishedAgain() {
        WikiComment comment = WikiComment.create(1L, 2L, null, AUTHOR, "小明", "内容");
        comment.publish(REVIEWER);

        comment.markDeleted(REVIEWER);
        assertEquals(WikiCommentStatus.DELETED, comment.getStatus());
        assertEquals(WikiCommentRagStatus.DISABLED, comment.getRagStatus());

        comment.restore(REVIEWER);
        assertEquals(WikiCommentStatus.PUBLISHED, comment.getStatus());
    }

    @Test
    void deleteDoesNotLeakIntoRepublishPath() {
        WikiComment comment = WikiComment.create(1L, 2L, null, AUTHOR, "小明", "内容");
        comment.markDeleted(REVIEWER);

        assertThrows(BizException.class, () -> comment.publish(REVIEWER));
        assertThrows(BizException.class, () -> comment.hide(REVIEWER));
    }

    @Test
    void pinningOnlyAllowedForPublishedCommentsAndHiddenClearsPin() {
        WikiComment comment = WikiComment.create(1L, 2L, null, AUTHOR, "小明", "内容");
        assertThrows(BizException.class, () -> comment.pin(REVIEWER));

        comment.publish(REVIEWER);
        comment.pin(REVIEWER);
        assertTrue(comment.isPinned());

        comment.hide(REVIEWER);
        assertFalse(comment.isPinned());
    }

    @Test
    void aiApprovalPublishesWithAuditableVerdict() {
        WikiComment comment = WikiComment.create(1L, 2L, null, AUTHOR, "小明", "写得清楚");
        comment.publishByAi("与页面主题相关", "openai/gpt-4o-mini");

        assertEquals(WikiCommentStatus.PUBLISHED, comment.getStatus());
        assertEquals(WikiCommentRagStatus.PENDING, comment.getRagStatus());
        assertEquals(WikiCommentReviewMode.AI, comment.getReviewMode());
        assertEquals(WikiCommentAiVerdict.APPROVED, comment.getAiVerdict());
        assertEquals("与页面主题相关", comment.getAiNote());
        assertEquals("openai/gpt-4o-mini", comment.getAiModel());
        assertNotNull(comment.getAiReviewedAt());
        // 机器判定不能伪装成人工审核：这里没有人工审核人
        assertNull(comment.getReviewedByUserId());
        assertTrue(comment.visibleToPublic());
    }

    @Test
    void aiRejectionAndUnavailabilityKeepTheCommentPendingForHumans() {
        WikiComment rejected = WikiComment.create(1L, 2L, null, AUTHOR, "小明", "加微信领资料");
        rejected.rejectByAi("广告引流", "openai/gpt-4o-mini");
        assertEquals(WikiCommentStatus.PENDING, rejected.getStatus());
        assertEquals(WikiCommentAiVerdict.REJECTED, rejected.getAiVerdict());
        assertFalse(rejected.visibleToPublic());

        WikiComment skipped = WikiComment.create(1L, 2L, null, AUTHOR, "小明", "正常发言");
        skipped.aiUnavailable("AI 审核失败：模型未配置，已转人工审核");
        assertEquals(WikiCommentStatus.PENDING, skipped.getStatus());
        assertEquals(WikiCommentAiVerdict.UNAVAILABLE, skipped.getAiVerdict());
        assertNull(skipped.getAiModel());
        assertNotNull(skipped.getAiReviewedAt());

        // 判定理由与模型标识都限长：审计字段不能把文档撑爆
        WikiComment longNote = WikiComment.create(1L, 2L, null, AUTHOR, "小明", "内容");
        longNote.rejectByAi("x".repeat(500), "m".repeat(400));
        assertEquals(WikiComment.MAX_AI_NOTE_LENGTH, longNote.getAiNote().length());
        assertEquals(WikiComment.MAX_AI_MODEL_LENGTH, longNote.getAiModel().length());

        // 已发布的评论不能再被记成「AI 判定待审核」：状态机不被绕过
        WikiComment published = WikiComment.create(1L, 2L, null, AUTHOR, "小明", "已发布");
        published.publish(REVIEWER);
        assertThrows(BizException.class, () -> published.rejectByAi("重复驳回", null));
        assertThrows(BizException.class, () -> published.aiUnavailable("重复判定"));
    }

    @Test
    void editingAndDeletingRequireTheOriginalAuthor() {
        WikiComment comment = WikiComment.create(1L, 2L, null, AUTHOR, "小明", "原始内容");

        assertThrows(BizException.class, () -> comment.updateContent(REVIEWER, "篡改内容"));
        assertThrows(BizException.class, () -> comment.deleteByAuthor(REVIEWER));

        comment.updateContent(AUTHOR, "作者修订");
        assertEquals("作者修订", comment.getContent());
        comment.deleteByAuthor(AUTHOR);
        assertEquals(WikiCommentStatus.DELETED, comment.getStatus());
    }

    @Test
    void anonymousCommentNeverCarriesUserId() {
        WikiComment anonymous = WikiComment.createAnonymous(1L, 2L, null, "访客", "内容");
        assertEquals(WikiCommentStatus.PENDING, anonymous.getStatus());
        assertNull(anonymous.getAuthorUserId());

        assertThrows(BizException.class, () -> WikiComment.create(1L, 2L, null, null, "佚名", "内容"));
        assertThrows(BizException.class, () -> WikiComment.createAnonymous(1L, 2L, null, "访客", "内容")
                .updateContent(AUTHOR, "编辑"));
    }

    @Test
    void legacyCommentWithoutAnchorStaysCompatible() {
        WikiComment comment = WikiComment.create(1L, 2L, null, AUTHOR, "小明", "内容");
        assertNull(comment.getAnchor());

        comment.publish(REVIEWER);
        comment.hide(REVIEWER);
        comment.restore(REVIEWER);
        comment.markDeleted(REVIEWER);
        assertNull(comment.getAnchor());

        // 回复无锚点根评论时同样保持无锚点
        WikiComment reply = WikiComment.reply(1L, 2L, 9L, AUTHOR, "小明", "回复", null);
        assertNull(reply.getAnchor());
        assertEquals(9L, reply.getParentId());
    }

    @Test
    void rootCommentKeepsTheServerStampedAnchor() {
        WikiTextAnchor anchor = anchor("快速开始", "hash-1");

        WikiComment comment = WikiComment.create(1L, 2L, null, AUTHOR, "小明", "内容", anchor);

        assertEquals(anchor, comment.getAnchor());
        assertEquals("快速开始", comment.getAnchor().exact());
        assertEquals(200L, comment.getAnchor().versionId());
        comment.publish(REVIEWER);
        assertEquals(anchor, comment.getAnchor());
    }

    @Test
    void repliesInheritRootAnchorAndCannotCarryAnother() {
        WikiTextAnchor rootAnchor = anchor("快速开始", "hash-1");

        WikiComment reply = WikiComment.reply(1L, 2L, 9L, AUTHOR, "小明", "回复", rootAnchor);
        assertEquals(rootAnchor, reply.getAnchor());
        assertEquals(9L, reply.getParentId());

        // 回复不允许自带锚点（只能继承根评论）
        assertThrows(BizException.class,
                () -> WikiComment.create(1L, 2L, 9L, AUTHOR, "小明", "回复", rootAnchor));
        // 回复必须有根评论
        assertThrows(BizException.class,
                () -> WikiComment.reply(1L, 2L, null, AUTHOR, "小明", "回复", rootAnchor));
    }

    private static WikiTextAnchor anchor(String exact, String contentHash) {
        return WikiTextAnchor.stamp(WikiTextAnchor.draft(exact, null, null, null, null, null),
                200L, 1L, contentHash, exact + " 的正文上下文");
    }
}
