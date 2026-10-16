package online.yudream.base.interfaces.platform.wiki.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.dto.WikiCommentDTO;
import online.yudream.base.application.platform.wiki.query.WikiCommentPageQuery;
import online.yudream.base.application.platform.wiki.service.WikiAccessPermissions;
import online.yudream.base.application.platform.wiki.service.WikiCommentManageAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiCommentWebAssembler;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiSpaceAccessWebAssembler;
import online.yudream.base.interfaces.platform.wiki.res.WikiCommentRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 治理端评论管理：分页列表与审核动作（发布/隐藏/恢复/置顶/软删除）。
 * <p>
 * 每个端点都要求全局评论权限码 + 空间实例 MANAGE；空间归属由评论自身推导；
 * 隐藏与删除均可恢复，评论权限不能只靠前端隐藏实现。
 */
@RestController
@RequestMapping("/api/platform/wiki")
@RequiredArgsConstructor
public class WikiCommentManageController {

    private final WikiCommentManageAppService service;

    @GetMapping("/spaces/{spaceId}/comments")
    @PermissionRegister(code = WikiAccessPermissions.COMMENT_VIEW, name = "查看 Wiki 评论", module = "平台能力",
            desc = "分页查看知识库评论（可按状态过滤）")
    public Result<PageResult<WikiCommentRes>> page(@PathVariable Long spaceId, WikiCommentPageQuery query) {
        return Result.ok(WikiCommentWebAssembler.toResPage(
                service.page(WikiSpaceAccessWebAssembler.commentQuery(spaceId, query))));
    }

    @PostMapping("/comments/{commentId}/publish")
    @PermissionRegister(code = WikiAccessPermissions.COMMENT_MANAGE, name = "发布 Wiki 评论", module = "平台能力",
            desc = "审核通过并公开评论")
    public Result<WikiCommentRes> publish(@PathVariable Long commentId) {
        return Result.ok(WikiCommentWebAssembler.toRes(service.publish(commentId)));
    }

    @PostMapping("/comments/{commentId}/hide")
    @PermissionRegister(code = WikiAccessPermissions.COMMENT_MANAGE, name = "隐藏 Wiki 评论", module = "平台能力",
            desc = "软下架评论并停止检索候选")
    public Result<WikiCommentRes> hide(@PathVariable Long commentId) {
        return Result.ok(WikiCommentWebAssembler.toRes(service.hide(commentId)));
    }

    @PostMapping("/comments/{commentId}/restore")
    @PermissionRegister(code = WikiAccessPermissions.COMMENT_MANAGE, name = "恢复 Wiki 评论", module = "平台能力",
            desc = "恢复隐藏或删除的评论")
    public Result<WikiCommentRes> restore(@PathVariable Long commentId) {
        return Result.ok(WikiCommentWebAssembler.toRes(service.restore(commentId)));
    }

    @PostMapping("/comments/{commentId}/delete")
    @PermissionRegister(code = WikiAccessPermissions.COMMENT_DELETE, name = "删除 Wiki 评论", module = "平台能力",
            desc = "软删除评论（保留审计记录，可恢复）")
    public Result<WikiCommentRes> delete(@PathVariable Long commentId) {
        return Result.ok(WikiCommentWebAssembler.toRes(service.delete(commentId)));
    }

    @PostMapping("/comments/{commentId}/pin")
    @PermissionRegister(code = WikiAccessPermissions.COMMENT_MANAGE, name = "置顶 Wiki 评论", module = "平台能力",
            desc = "置顶已发布评论")
    public Result<WikiCommentRes> pin(@PathVariable Long commentId) {
        return Result.ok(WikiCommentWebAssembler.toRes(service.pin(commentId)));
    }

    @PostMapping("/comments/{commentId}/unpin")
    @PermissionRegister(code = WikiAccessPermissions.COMMENT_MANAGE, name = "取消置顶 Wiki 评论", module = "平台能力",
            desc = "取消评论置顶")
    public Result<WikiCommentRes> unpin(@PathVariable Long commentId) {
        return Result.ok(WikiCommentWebAssembler.toRes(service.unpin(commentId)));
    }
}
