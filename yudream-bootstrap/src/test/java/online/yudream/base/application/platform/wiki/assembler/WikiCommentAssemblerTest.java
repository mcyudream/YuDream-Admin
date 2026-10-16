package online.yudream.base.application.platform.wiki.assembler;

import online.yudream.base.application.platform.wiki.dto.WikiCommentDTO;
import online.yudream.base.domain.platform.wiki.aggregate.WikiComment;
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.enumerate.WikiTextAnchorState;
import online.yudream.base.domain.platform.wiki.valobj.WikiTextAnchor;
import online.yudream.base.domain.platform.wiki.valobj.WikiTextBlockHint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 评论应用装配：公开视图隐藏空间/作者/审核人身份并按当前发布版本计算锚点状态；
 * 锚点对象扁平（引用文本 + 定位提示），版本身份与状态和 anchor 平级；
 * 治理视图保留归属与审核字段且不计算状态。
 */
class WikiCommentAssemblerTest {

    private static final String BODY = "欢迎来到快速开始页面。";

    @Test
    void publicViewHidesIdentityFieldsAndComputesAnchorState() {
        WikiComment comment = publishedComment();
        WikiCommentDTO dto = WikiCommentAssembler.publicView(comment, List.of(), publishedVersion("hash-1"));

        assertThat(dto.getSpaceId()).isNull();
        assertThat(dto.getAuthorUserId()).isNull();
        assertThat(dto.getReviewedByUserId()).isNull();
        assertThat(dto.getReviewedAt()).isNull();
        assertThat(dto.getAuthorName()).isEqualTo("小明");
        assertThat(dto.getNodeId()).isEqualTo("20");

        assertThat(dto.getAnchor()).isNotNull();
        assertThat(dto.getAnchor().getExact()).isEqualTo("快速开始");
        assertThat(dto.getAnchor().getPrefix()).isEqualTo("欢迎来到");
        assertThat(dto.getAnchor().getSuffix()).isEqualTo("页面。");
        assertThat(dto.getAnchor().getBlockOrdinal()).isEqualTo(4);
        assertThat(dto.getAnchor().getBlockType()).isEqualTo("tableCell");
        assertThat(dto.getAnchor().getTableRow()).isEqualTo(1);
        assertThat(dto.getAnchor().getTableCell()).isEqualTo(2);
        assertThat(dto.getAnchor().getStartOffset()).isEqualTo(3);
        assertThat(dto.getAnchor().getEndOffset()).isEqualTo(8);
        // 版本身份与状态和 anchor 平级下发，客户端可直接映射为锚点 scope
        assertThat(dto.getVersionId()).isEqualTo("200");
        assertThat(dto.getRevision()).isEqualTo("1");
        assertThat(dto.getContentHash()).isEqualTo("hash-1");
        assertThat(dto.getAnchorState()).isEqualTo(WikiTextAnchorState.ACTIVE.wireValue());
    }

    @Test
    void publicViewMarksChangedPageAsReanchorableAndKeepsComment() {
        WikiCommentDTO dto = WikiCommentAssembler.publicView(publishedComment(), List.of(),
                publishedVersion("hash-2"));

        assertThat(dto.getContent()).isEqualTo("这里需要说明");
        assertThat(dto.getAnchorState()).isEqualTo(WikiTextAnchorState.REANCHORABLE.wireValue());
    }

    @Test
    void publicViewWithoutPublishedContextKeepsAnchorAndLeavesStateNull() {
        WikiCommentDTO dto = WikiCommentAssembler.publicView(publishedComment(), List.of());

        assertThat(dto.getAnchor().getExact()).isEqualTo("快速开始");
        assertThat(dto.getVersionId()).isEqualTo("200");
        assertThat(dto.getAnchorState()).isNull();
    }

    @Test
    void publicViewKeepsLegacyCommentVisibleWithoutAnchor() {
        WikiComment legacy = WikiComment.create(10L, 20L, null, 100L, "小明", "历史评论");
        legacy.setId(9L);
        legacy.publish(7L);

        WikiCommentDTO dto = WikiCommentAssembler.publicView(legacy, List.of(), publishedVersion("hash-1"));

        assertThat(dto.getAnchor()).isNull();
        assertThat(dto.getVersionId()).isNull();
        assertThat(dto.getRevision()).isNull();
        assertThat(dto.getContentHash()).isNull();
        assertThat(dto.getAnchorState()).isNull();
        assertThat(dto.getStatus()).isEqualTo("PUBLISHED");
        assertThat(dto.getSpaceId()).isNull();
    }

    @Test
    void manageKeepsOwnershipAndReviewFieldsWithoutAnchorState() {
        WikiCommentDTO dto = WikiCommentAssembler.manage(publishedComment(), List.of());

        assertThat(dto.getSpaceId()).isEqualTo("10");
        assertThat(dto.getAuthorUserId()).isEqualTo("100");
        assertThat(dto.getReviewedByUserId()).isEqualTo("7");
        assertThat(dto.getReviewedAt()).isNotNull();
        assertThat(dto.getVersionId()).isEqualTo("200");
        assertThat(dto.getRevision()).isEqualTo("1");
        assertThat(dto.getAnchorState()).isNull();
    }

    private static WikiComment publishedComment() {
        WikiTextAnchor anchor = WikiTextAnchor.stamp(WikiTextAnchor.draft("快速开始", "欢迎来到", "页面。",
                        WikiTextBlockHint.of("tableCell", 4, 1, 2), 3, 8),
                200L, 1L, "hash-1", BODY);
        WikiComment comment = WikiComment.create(10L, 20L, null, 100L, "小明", "这里需要说明", anchor);
        comment.setId(5L);
        comment.publish(7L);
        return comment;
    }

    private static WikiPageVersion publishedVersion(String contentHash) {
        WikiPageVersion version = WikiPageVersion.builder().spaceId(10L).nodeId(20L).revision(1L)
                .markdown("---\ntitle: 页面\ntype: concept\n---\n" + BODY + "\n").contentHash(contentHash).build();
        version.setId(200L);
        return version;
    }
}
