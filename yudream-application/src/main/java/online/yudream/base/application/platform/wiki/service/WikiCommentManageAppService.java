package online.yudream.base.application.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.assembler.WikiCommentAssembler;
import online.yudream.base.application.platform.wiki.dto.WikiCommentDTO;
import online.yudream.base.application.platform.wiki.query.WikiCommentPageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiComment;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.platform.wiki.repo.WikiCommentRepo;
import online.yudream.base.domain.platform.wiki.valobj.WikiActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 治理端评论用例：分页列表与审核动作。
 * <p>
 * 每个用例都过能力闸门 + 全局 {@code platform:wiki:comment:*} 权限码 + 空间实例 MANAGE；
 * 空间归属由评论自身推导，不接受客户端传入的 spaceId；隐藏/删除是软状态且可恢复，
 * 所有动作记录审核人与时间。评论 RAG 状态只随审核状态翻转，索引接入属于后续批次。
 */
@Service
@RequiredArgsConstructor
public class WikiCommentManageAppService {

    private final WikiCommentRepo commentRepo;
    private final WikiSpaceAclAppService accessAppService;

    @Transactional(readOnly = true)
    public PageResult<WikiCommentDTO> page(WikiCommentPageQuery query) {
        WikiSpace space = accessAppService.requireSpace(query.getSpaceId(), WikiSpaceAclAction.READ,
                WikiAccessPermissions.COMMENT_VIEW).space();
        PageResult<WikiComment> page = commentRepo.pageBySpace(space.getId(), query.getStatus(),
                query.getPage(), query.getSize());
        return new PageResult<>(page.getRecords().stream()
                .map(comment -> WikiCommentAssembler.manage(comment, List.of()))
                .toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    /** 审核发布：待审核评论转公开。 */
    @Transactional
    public WikiCommentDTO publish(Long commentId) {
        return moderate(commentId, WikiAccessPermissions.COMMENT_MANAGE,
                (comment, actor) -> comment.publish(actor.userId()));
    }

    /** 隐藏（软下架）：公开端与检索候选立即移除，可恢复。 */
    @Transactional
    public WikiCommentDTO hide(Long commentId) {
        return moderate(commentId, WikiAccessPermissions.COMMENT_MANAGE,
                (comment, actor) -> comment.hide(actor.userId()));
    }

    /** 恢复隐藏/删除的评论。 */
    @Transactional
    public WikiCommentDTO restore(Long commentId) {
        return moderate(commentId, WikiAccessPermissions.COMMENT_MANAGE,
                (comment, actor) -> comment.restore(actor.userId()));
    }

    /** 软删除评论（可恢复）。 */
    @Transactional
    public WikiCommentDTO delete(Long commentId) {
        return moderate(commentId, WikiAccessPermissions.COMMENT_DELETE,
                (comment, actor) -> comment.markDeleted(actor.userId()));
    }

    @Transactional
    public WikiCommentDTO pin(Long commentId) {
        return moderate(commentId, WikiAccessPermissions.COMMENT_MANAGE,
                (comment, actor) -> comment.pin(actor.userId()));
    }

    @Transactional
    public WikiCommentDTO unpin(Long commentId) {
        return moderate(commentId, WikiAccessPermissions.COMMENT_MANAGE,
                (comment, actor) -> comment.unpin(actor.userId()));
    }

    private WikiCommentDTO moderate(Long commentId, String permissionCode, ModerateAction action) {
        WikiActor actor = accessAppService.requireGlobalPermission(permissionCode);
        WikiComment comment = comment(commentId);
        accessAppService.requireSpaceAcl(comment.getSpaceId(), WikiSpaceAclAction.MANAGE);
        action.apply(comment, actor);
        return WikiCommentAssembler.manage(commentRepo.save(comment), List.of());
    }

    private WikiComment comment(Long commentId) {
        if (commentId == null) {
            throw new BizException("评论不能为空");
        }
        return commentRepo.findById(commentId).orElseThrow(() -> new BizException("评论不存在"));
    }

    @FunctionalInterface
    private interface ModerateAction {
        void apply(WikiComment comment, WikiActor actor);
    }
}
