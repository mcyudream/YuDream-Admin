package online.yudream.base.interfaces.platform.wiki.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.service.WikiReviewAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiKnowledgeWebAssembler;
import online.yudream.base.interfaces.platform.wiki.request.WikiReviewExecuteRequest;
import online.yudream.base.interfaces.platform.wiki.res.WikiReviewItemRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 异步审阅队列。
 */
@RestController
@RequestMapping("/api/platform/wiki")
@RequiredArgsConstructor
public class WikiReviewController {

    private final WikiReviewAppService service;

    @GetMapping("/spaces/{spaceId}/reviews")
    @PermissionRegister(code = "platform:wiki:view", name = "查看 Wiki 审阅项", module = "平台能力", desc = "查看摄入时 LLM 标记的待人工判断项")
    public Result<List<WikiReviewItemRes>> list(@PathVariable Long spaceId) {
        return Result.ok(WikiKnowledgeWebAssembler.toReviewItemResList(service.list(spaceId)));
    }

    @GetMapping("/spaces/{spaceId}/reviews/pending")
    @PermissionRegister(code = "platform:wiki:view", name = "查看待处理审阅项", module = "平台能力", desc = "查看待处理的 Wiki 审阅项")
    public Result<List<WikiReviewItemRes>> pending(@PathVariable Long spaceId) {
        return Result.ok(WikiKnowledgeWebAssembler.toReviewItemResList(service.pending(spaceId)));
    }

    @PostMapping("/reviews/{id}/resolve")
    @PermissionRegister(code = "platform:wiki:edit", name = "完成 Wiki 审阅项", module = "平台能力", desc = "标记审阅项为已完成")
    public Result<Void> resolve(@PathVariable Long id) {
        service.resolve(id);
        return Result.ok();
    }

    @PostMapping("/reviews/{id}/dismiss")
    @PermissionRegister(code = "platform:wiki:edit", name = "忽略 Wiki 审阅项", module = "平台能力", desc = "标记审阅项为已忽略")
    public Result<Void> dismiss(@PathVariable Long id) {
        service.dismiss(id);
        return Result.ok();
    }

    @PostMapping("/reviews/{id}/execute")
    @PermissionRegister(code = "platform:wiki:edit", name = "执行 Wiki 审阅动作", module = "平台能力", desc = "执行完成/忽略/触发深度研究等预定义动作")
    public Result<Void> execute(@PathVariable Long id, @Valid @RequestBody WikiReviewExecuteRequest request) {
        service.execute(id, request.getAction());
        return Result.ok();
    }
}
