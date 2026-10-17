package online.yudream.base.interfaces.platform.ai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.ai.query.AiSystemRunsQuery;
import online.yudream.base.application.platform.ai.service.AiSystemRunAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.ai.assembler.AiRunWebAssembler;
import online.yudream.base.interfaces.platform.ai.request.AiInteractionAnswerRequest;
import online.yudream.base.interfaces.platform.ai.res.AiRunRes;
import online.yudream.base.interfaces.platform.ai.support.AiRunStreamSupport;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;

@RestController
@RequestMapping("/api/platform/ai/system-runs")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class AiSystemRunController {
    private final AiSystemRunAppService systemRuns;
    private final AiRunStreamSupport streams;

    @GetMapping
    @PermissionRegister(code = "platform:ai:system:view", name = "查看系统 AI 运行", module = "平台能力", desc = "查看系统预算后台运行，不混入个人助手")
    public Result<List<AiRunRes>> list(AiSystemRunsQuery query) {
        var principal = SecurityPrincipalSupport.current();
        return Result.ok(AiRunWebAssembler.runs(systemRuns.list(principal.userId(), principal.permissions(), query)));
    }
    @GetMapping("/{id}")
    @PermissionRegister(code = "platform:ai:system:view", name = "查看系统 AI 运行", module = "平台能力", desc = "查看系统预算后台运行详情")
    public Result<AiRunRes> get(@PathVariable String id) {
        var principal = SecurityPrincipalSupport.current();
        return Result.ok(AiRunWebAssembler.run(systemRuns.get(principal.userId(), principal.permissions(), id)));
    }
    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PermissionRegister(code = "platform:ai:system:view", name = "查看系统 AI 进度", module = "平台能力", desc = "从持久游标订阅系统预算运行")
    public SseEmitter events(@PathVariable String id, @RequestParam(defaultValue = "0") long after) {
        var principal = SecurityPrincipalSupport.current();
        systemRuns.get(principal.userId(), principal.permissions(), id);
        return streams.subscribe(0L, id, after);
    }
    @PostMapping("/{id}/cancel")
    @PermissionRegister(code = "platform:ai:system:edit", name = "取消系统 AI 运行", module = "平台能力", desc = "明确终止系统后台任务及原生待处理交互")
    public Result<AiRunRes> cancel(@PathVariable String id) {
        var principal = SecurityPrincipalSupport.current();
        return Result.ok(AiRunWebAssembler.run(systemRuns.cancel(principal.userId(), principal.permissions(), id)));
    }
    @PostMapping("/{id}/interactions/{requestId}")
    @PermissionRegister(code = "platform:ai:system:accept", name = "审批系统 AI 工具", module = "平台能力", desc = "按原工具白名单及当前权限批准或拒绝系统任务交互")
    public Result<AiRunRes> answer(@PathVariable String id, @PathVariable String requestId, @Valid @RequestBody AiInteractionAnswerRequest request) {
        var principal = SecurityPrincipalSupport.current();
        return Result.ok(AiRunWebAssembler.run(systemRuns.answer(principal.userId(), principal.permissions(), id, requestId, request.approved(), request.output())));
    }
}
