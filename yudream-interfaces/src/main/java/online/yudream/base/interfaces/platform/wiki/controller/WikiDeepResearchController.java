package online.yudream.base.interfaces.platform.wiki.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.service.WikiDeepResearchAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiKnowledgeWebAssembler;
import online.yudream.base.interfaces.platform.wiki.request.WikiResearchPlanRequest;
import online.yudream.base.interfaces.platform.wiki.request.WikiResearchStartRequest;
import online.yudream.base.interfaces.platform.wiki.res.WikiResearchPlanRes;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 深度研究：LLM 生成研究主题与查询，确认后启动网络搜索与合成。
 */
@RestController
@RequestMapping("/api/platform/wiki")
@RequiredArgsConstructor
public class WikiDeepResearchController {

    private final WikiDeepResearchAppService service;

    @PostMapping("/spaces/{spaceId}/research/plan")
    @PermissionRegister(code = "platform:wiki:edit", name = "生成研究计划", module = "平台能力", desc = "由 LLM 生成领域精准的研究主题与搜索查询")
    public Result<WikiResearchPlanRes> plan(@PathVariable Long spaceId,
                                            @Valid @RequestBody WikiResearchPlanRequest request) {
        return Result.ok(WikiKnowledgeWebAssembler.toRes(service.plan(spaceId, request.getSeed())));
    }

    @PostMapping("/spaces/{spaceId}/research/start")
    @PermissionRegister(code = "platform:wiki:edit", name = "启动深度研究", module = "平台能力", desc = "将研究主题与查询入队执行网络搜索与综合")
    public Result<Void> start(@PathVariable Long spaceId,
                              @Valid @RequestBody WikiResearchStartRequest request) {
        service.start(spaceId, request.getTopic(), request.getQueries());
        return Result.ok();
    }
}
