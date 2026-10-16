package online.yudream.base.interfaces.platform.wiki.assembler;

import online.yudream.base.application.platform.wiki.cmd.WikiCommentAnchorInput;
import online.yudream.base.application.platform.wiki.cmd.WikiCommentCreateCmd;
import online.yudream.base.application.platform.wiki.cmd.WikiCommentUpdateCmd;
import online.yudream.base.application.platform.wiki.dto.WikiCommentAnchorDTO;
import online.yudream.base.application.platform.wiki.dto.WikiCommentDTO;
import online.yudream.base.application.platform.wiki.query.WikiPublicCommentPageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.interfaces.platform.wiki.request.WikiCommentAnchorRequest;
import online.yudream.base.interfaces.platform.wiki.request.WikiCommentCreateRequest;
import online.yudream.base.interfaces.platform.wiki.request.WikiCommentUpdateRequest;
import online.yudream.base.interfaces.platform.wiki.res.WikiCommentAnchorRes;
import online.yudream.base.interfaces.platform.wiki.res.WikiCommentRes;

import java.util.List;

/**
 * 评论接口层转换：request -> cmd、DTO -> res、公开分页参数归一。
 * 作者与空间归属一律由服务端解析，请求只提交页面、父评论、正文与锚点草稿；
 * 锚点草稿字段与公开端捕获组件同构（扁平），版本/哈希/状态只在响应侧、与 anchor 平级出现。
 */
public final class WikiCommentWebAssembler {

    private static final int MAX_PAGE_SIZE = 100;

    private WikiCommentWebAssembler() {
    }

    public static WikiCommentCreateCmd create(String spaceSlug, Long nodeId, WikiCommentCreateRequest request) {
        WikiCommentCreateCmd cmd = new WikiCommentCreateCmd();
        cmd.setSpaceSlug(spaceSlug);
        cmd.setNodeId(nodeId);
        cmd.setParentId(parseId(request.getParentId()));
        cmd.setContent(request.getContent());
        cmd.setAnchor(anchorInput(request.getAnchor()));
        return cmd;
    }

    public static WikiCommentUpdateCmd update(String spaceSlug, Long commentId, WikiCommentUpdateRequest request) {
        WikiCommentUpdateCmd cmd = new WikiCommentUpdateCmd();
        cmd.setSpaceSlug(spaceSlug);
        cmd.setCommentId(commentId);
        cmd.setContent(request.getContent());
        return cmd;
    }

    public static WikiPublicCommentPageQuery publicQuery(WikiPublicCommentPageQuery query) {
        WikiPublicCommentPageQuery normalized = query == null ? new WikiPublicCommentPageQuery() : query;
        normalized.setPage(Math.max(normalized.getPage(), 1));
        normalized.setSize(Math.min(Math.max(normalized.getSize(), 1), MAX_PAGE_SIZE));
        return normalized;
    }

    public static PageResult<WikiCommentRes> toResPage(PageResult<WikiCommentDTO> page) {
        return new PageResult<>(page.getRecords().stream().map(WikiCommentWebAssembler::toRes).toList(),
                page.getTotal(), page.getPage(), page.getSize());
    }

    public static WikiCommentRes toRes(WikiCommentDTO dto) {
        if (dto == null) {
            return null;
        }
        List<WikiCommentRes> replies = dto.getReplies() == null ? List.of()
                : dto.getReplies().stream().map(WikiCommentWebAssembler::toRes).toList();
        return WikiCommentRes.builder()
                .id(dto.getId())
                .spaceId(dto.getSpaceId())
                .nodeId(dto.getNodeId())
                .parentId(dto.getParentId())
                .authorType(dto.getAuthorType())
                .authorUserId(dto.getAuthorUserId())
                .authorName(dto.getAuthorName())
                .content(dto.getContent())
                .anchor(anchorRes(dto.getAnchor()))
                .versionId(dto.getVersionId())
                .revision(dto.getRevision())
                .contentHash(dto.getContentHash())
                .anchorState(dto.getAnchorState())
                .status(dto.getStatus())
                .pinned(dto.isPinned())
                .reviewedByUserId(dto.getReviewedByUserId())
                .reviewedAt(dto.getReviewedAt())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .replies(replies)
                .build();
    }

    private static WikiCommentAnchorInput anchorInput(WikiCommentAnchorRequest request) {
        if (request == null) {
            return null;
        }
        WikiCommentAnchorInput input = new WikiCommentAnchorInput();
        input.setExact(request.getExact());
        input.setPrefix(request.getPrefix());
        input.setSuffix(request.getSuffix());
        input.setBlockOrdinal(request.getBlockOrdinal());
        input.setBlockType(request.getBlockType());
        input.setTableRow(request.getTableRow());
        input.setTableCell(request.getTableCell());
        input.setStartOffset(request.getStartOffset());
        input.setEndOffset(request.getEndOffset());
        return input;
    }

    private static WikiCommentAnchorRes anchorRes(WikiCommentAnchorDTO dto) {
        if (dto == null) {
            return null;
        }
        return WikiCommentAnchorRes.builder()
                .exact(dto.getExact())
                .prefix(dto.getPrefix())
                .suffix(dto.getSuffix())
                .blockOrdinal(dto.getBlockOrdinal())
                .blockType(dto.getBlockType())
                .tableRow(dto.getTableRow())
                .tableCell(dto.getTableCell())
                .startOffset(dto.getStartOffset())
                .endOffset(dto.getEndOffset())
                .build();
    }

    private static Long parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        }
        catch (NumberFormatException error) {
            throw new BizException("父评论 ID 不合法");
        }
    }
}
