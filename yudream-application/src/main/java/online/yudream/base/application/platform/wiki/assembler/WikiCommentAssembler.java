package online.yudream.base.application.platform.wiki.assembler;

import online.yudream.base.application.platform.wiki.dto.WikiCommentAnchorDTO;
import online.yudream.base.application.platform.wiki.dto.WikiCommentDTO;
import online.yudream.base.domain.platform.wiki.aggregate.WikiComment;
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCommentAuthorType;
import online.yudream.base.domain.platform.wiki.valobj.WikiFrontmatter;
import online.yudream.base.domain.platform.wiki.valobj.WikiTextAnchor;
import online.yudream.base.domain.platform.wiki.valobj.WikiTextBlockHint;

import java.util.List;

/**
 * 评论领域到应用 DTO 的转换。
 * <p>
 * 公开视图不暴露空间与作者身份字段（spaceId/authorUserId/审核人），并基于当前已发布版本
 * 计算锚点状态；治理视图保留归属与审核字段，锚点状态不计算（保持 null）。
 * 锚点对象是扁平结构（引用文本 + 定位提示），版本身份与状态和 anchor 平级下发。
 */
public final class WikiCommentAssembler {

    private WikiCommentAssembler() {
    }

    /** 治理端视图：保留审核字段、空间/作者归属与全部状态。 */
    public static WikiCommentDTO manage(WikiComment comment, List<WikiCommentDTO> replies) {
        WikiCommentDTO dto = base(comment, replies, true);
        applyAnchor(dto, comment.getAnchor(), null);
        return dto;
    }

    /** 公开端视图（无已发布版本上下文）：锚点保留但状态为 null。 */
    public static WikiCommentDTO publicView(WikiComment comment, List<WikiCommentDTO> replies) {
        return publicView(comment, replies, null);
    }

    /**
     * 公开端视图：隐藏空间/作者/审核人身份字段，并按当前已发布版本计算锚点状态。
     * <p>
     * 锚点失配（reanchorable/orphaned）只影响高亮能力，评论本身保持可见。
     */
    public static WikiCommentDTO publicView(WikiComment comment, List<WikiCommentDTO> replies,
                                            WikiPageVersion publishedVersion) {
        WikiCommentDTO dto = base(comment, replies, false);
        dto.setSpaceId(null);
        dto.setAuthorUserId(null);
        dto.setReviewedByUserId(null);
        dto.setReviewedAt(null);
        applyAnchor(dto, comment.getAnchor(), publishedVersion);
        return dto;
    }

    private static WikiCommentDTO base(WikiComment comment, List<WikiCommentDTO> replies, boolean withReview) {
        WikiCommentAuthorType authorType = comment.getAuthorType();
        return WikiCommentDTO.builder()
                .id(id(comment.getId()))
                .spaceId(id(comment.getSpaceId()))
                .nodeId(id(comment.getNodeId()))
                .parentId(id(comment.getParentId()))
                .authorType(authorType == null ? null : authorType.name())
                .authorUserId(id(comment.getAuthorUserId()))
                .authorName(comment.getAuthorName())
                .content(comment.getContent())
                .status(comment.getStatus() == null ? null : comment.getStatus().name())
                .pinned(comment.isPinned())
                .reviewedByUserId(withReview ? id(comment.getReviewedByUserId()) : null)
                .reviewedAt(withReview ? comment.getReviewedAt() : null)
                .reviewMode(withReview && comment.getReviewMode() != null ? comment.getReviewMode().name() : null)
                .aiVerdict(withReview && comment.getAiVerdict() != null ? comment.getAiVerdict().wireValue() : null)
                .aiNote(withReview ? comment.getAiNote() : null)
                .aiModel(withReview ? comment.getAiModel() : null)
                .aiReviewedAt(withReview ? comment.getAiReviewedAt() : null)
                .createTime(comment.getCreateTime())
                .updateTime(comment.getUpdateTime())
                .replies(replies == null ? List.of() : replies)
                .build();
    }

    private static void applyAnchor(WikiCommentDTO dto, WikiTextAnchor anchor, WikiPageVersion publishedVersion) {
        if (anchor == null) {
            return;
        }
        dto.setAnchor(anchorView(anchor));
        dto.setVersionId(id(anchor.versionId()));
        dto.setRevision(String.valueOf(anchor.revision()));
        dto.setContentHash(anchor.contentHash());
        dto.setAnchorState(anchorState(anchor, publishedVersion));
    }

    private static WikiCommentAnchorDTO anchorView(WikiTextAnchor anchor) {
        WikiTextBlockHint blockHint = anchor.blockHint();
        return WikiCommentAnchorDTO.builder()
                .exact(anchor.exact())
                .prefix(anchor.prefix())
                .suffix(anchor.suffix())
                .blockOrdinal(blockHint == null ? null : blockHint.ordinal())
                .blockType(blockHint == null ? null : blockHint.type().wireValue())
                .tableRow(blockHint == null ? null : blockHint.tableRow())
                .tableCell(blockHint == null ? null : blockHint.tableCell())
                .startOffset(anchor.startOffset())
                .endOffset(anchor.endOffset())
                .build();
    }

    private static String anchorState(WikiTextAnchor anchor, WikiPageVersion publishedVersion) {
        if (publishedVersion == null || publishedVersion.getMarkdown() == null) {
            return null;
        }
        String body = WikiFrontmatter.parse(publishedVersion.getMarkdown()).bodyOnly();
        return anchor.stateAgainst(publishedVersion.getContentHash(), body).wireValue();
    }

    private static String id(Long value) {
        return value == null ? null : value.toString();
    }
}
