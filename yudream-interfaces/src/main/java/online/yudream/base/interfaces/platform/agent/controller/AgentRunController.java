package online.yudream.base.interfaces.platform.agent.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.ai.service.AiBusinessRunAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.agent.assembler.AgentWebAssembler;
import online.yudream.base.interfaces.platform.agent.request.AgentRunRequest;
import online.yudream.base.interfaces.platform.ai.assembler.AiRunWebAssembler;
import online.yudream.base.interfaces.platform.ai.res.AiRunRes;
import online.yudream.base.interfaces.platform.ai.support.AiRunStreamSupport;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/platform/agents")
@RequiredArgsConstructor
@ConditionalOnExpression("${yudream.platform.capabilities.ai.enabled:false} && ${yudream.platform.capabilities.agent.enabled:false}")
public class AgentRunController {
    private final AiBusinessRunAppService businessRuns;
    private final AiRunStreamSupport streams;
    @PostMapping("/{id}/run")
    @PermissionRegister(code = "platform:agent:run", name = "运行 Agent 应用", module = "平台能力", desc = "提交后台运行，通过统一事件流展示进度")
    public Result<AiRunRes> run(@PathVariable Long id, @Valid @RequestBody AgentRunRequest request) {
        return Result.ok(AiRunWebAssembler.run(businessRuns.agent(AgentWebAssembler.toRunCmd(id, request, SecurityPrincipalSupport.current()))));
    }
    @PostMapping(value = "/{id}/debug/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PermissionRegister(code = "platform:agent:edit", name = "调试 Agent 应用", module = "平台能力", desc = "订阅后台 Agent 工作流调试")
    public SseEmitter debug(@PathVariable Long id, @Valid @RequestBody AgentRunRequest request) {
        var principal = SecurityPrincipalSupport.current();
        var run = businessRuns.debug(AgentWebAssembler.toRunCmd(id, request, principal));
        return streams.subscribe(principal.userId(), run.id(), 0, true);
    }
    @PostMapping("/{id}/debug/runs")
    @PermissionRegister(code = "platform:agent:edit", name = "提交 Agent 调试", module = "平台能力", desc = "返回持久调试任务并观察真实节点进度")
    public Result<AiRunRes> startDebug(@PathVariable Long id, @Valid @RequestBody AgentRunRequest request) {
        return Result.ok(AiRunWebAssembler.run(businessRuns.debug(AgentWebAssembler.toRunCmd(id, request, SecurityPrincipalSupport.current()))));
    }
}
