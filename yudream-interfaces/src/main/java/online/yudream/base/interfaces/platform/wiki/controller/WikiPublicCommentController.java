package online.yudream.base.interfaces.platform.wiki.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.query.WikiPublicCommentPageQuery;
import online.yudream.base.application.platform.wiki.service.WikiCommentAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiCommentWebAssembler;
import online.yudream.base.interfaces.platform.wiki.request.WikiCommentCreateRequest;
import online.yudream.base.interfaces.platform.wiki.request.WikiCommentUpdateRequest;
import online.yudream.base.interfaces.platform.wiki.res.WikiCommentRes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开端评论：只读已发布评论；发表/编辑/删除要求登录并由服务端校验作者本人。
 * 匿名发表未开放（默认关闭），评论开关未开启的空间一律拒绝。
 */
@RestController
@RequestMapping("/api/public/wiki")
@RequiredArgsConstructor
public class WikiPublicCommentController {

    private final WikiCommentAppService service;

    @GetMapping("/{spaceSlug}/nodes/{nodeId}/comments")
    public Result<PageResult<WikiCommentRes>> page(@PathVariable String spaceSlug, @PathVariable Long nodeId,
                                                   WikiPublicCommentPageQuery query) {
        return Result.ok(WikiCommentWebAssembler.toResPage(
                service.page(spaceSlug, nodeId, WikiCommentWebAssembler.publicQuery(query))));
    }

    @PostMapping("/{spaceSlug}/nodes/{nodeId}/comments")
    public Result<WikiCommentRes> create(@PathVariable String spaceSlug, @PathVariable Long nodeId,
                                         @Valid @RequestBody WikiCommentCreateRequest request) {
        return Result.ok(WikiCommentWebAssembler.toRes(
                service.create(WikiCommentWebAssembler.create(spaceSlug, nodeId, request))));
    }

    @PutMapping("/{spaceSlug}/comments/{commentId}")
    public Result<WikiCommentRes> update(@PathVariable String spaceSlug, @PathVariable Long commentId,
                                         @Valid @RequestBody WikiCommentUpdateRequest request) {
        return Result.ok(WikiCommentWebAssembler.toRes(
                service.update(WikiCommentWebAssembler.update(spaceSlug, commentId, request))));
    }

    @DeleteMapping("/{spaceSlug}/comments/{commentId}")
    public Result<Void> delete(@PathVariable String spaceSlug, @PathVariable Long commentId) {
        service.delete(spaceSlug, commentId);
        return Result.ok();
    }
}
