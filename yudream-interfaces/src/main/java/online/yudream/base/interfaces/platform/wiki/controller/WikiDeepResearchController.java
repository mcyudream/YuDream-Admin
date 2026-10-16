package online.yudream.base.interfaces.platform.wiki.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.ai.service.AiUsageExecutionScope;
import online.yudream.base.interfaces.platform.ai.assembler.AiRunWebAssembler;
import online.yudream.base.interfaces.platform.ai.res.AiRunRes;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import online.yudream.base.application.platform.wiki.service.WikiDeepResearchAppService;
import online.yudream.base.domain.platform.wiki.anno.WikiSpaceAccess;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.wiki.request.WikiResearchPlanRequest;
import online.yudream.base.interfaces.platform.wiki.request.WikiResearchStartRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 深度研究：LLM 生成研究主题与查询，确认后启动网络搜索与合成。
 */
@RestController
@ConditionalOnProperty(name = {"yudream.platform.capabilities.ai.enabled", "yudream.platform.capabilities.wiki.enabled"}, havingValue = "true")
@RequestMapping("/api/platform/wiki")
@RequiredArgsConstructor
public class WikiDeepResearchController {

    private final WikiDeepResearchAppService service;

    @PostMapping({"/spaces/{spaceId}/research/plan", "/spaces/{spaceId}/research/plan/runs"})
    @PermissionRegister(code = "platform:wiki:edit", name = "生成研究计划", module = "平台能力", desc = "由 LLM 生成领域精准的研究主题与搜索查询")
    @WikiSpaceAccess(action = WikiSpaceAclAction.EDIT, permission = "platform:wiki:edit")
    public Result<AiRunRes> plan(@PathVariable Long spaceId,
                                            @Valid @RequestBody WikiResearchPlanRequest request) {
        return Result.ok(AiRunWebAssembler.run(service.startPlan(SecurityPrincipalSupport.current().userId(), spaceId, request.getSeed())));
    }

    @PostMapping("/spaces/{spaceId}/research/start")
    @PermissionRegister(code = "platform:wiki:edit", name = "启动深度研究", module = "平台能力", desc = "将研究主题与查询入队执行网络搜索与综合")
    @WikiSpaceAccess(action = WikiSpaceAclAction.EDIT, permission = "platform:wiki:edit")
    public Result<Void> start(@PathVariable Long spaceId,
                              @Valid @RequestBody WikiResearchStartRequest request) {
        try (AiUsageExecutionScope ignored = AiUsageExecutionScope.open(SecurityPrincipalSupport.current().userId())) {
            service.start(spaceId, request.getTopic(), request.getQueries());
            return Result.ok();
        }
    }
}
