package online.yudream.base.interfaces.platform.ai.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.ai.service.AiRunAppService;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.ai.assembler.AiRunWebAssembler;
import online.yudream.base.interfaces.platform.ai.request.AiFrontendToolResultRequest;
import online.yudream.base.interfaces.platform.ai.res.AiRunRes;
import online.yudream.base.interfaces.platform.ai.support.AiRunStreamSupport;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;

@RestController
@RequestMapping("/api/platform/ai/runs")
@RequiredArgsConstructor
@SaCheckLogin
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class AiRunController {
    private final AiRunAppService runs;
    private final AiRunStreamSupport streams;
    @GetMapping
    public Result<List<AiRunRes>> list(@RequestParam(required = false) String sessionId,
                                      @RequestParam(defaultValue = "false") boolean activeOnly) {
        return Result.ok(AiRunWebAssembler.runs(runs.list(SecurityPrincipalSupport.current().userId(), sessionId, activeOnly)));
    }
    @GetMapping("/{id}")
    public Result<AiRunRes> get(@PathVariable String id) {
        return Result.ok(AiRunWebAssembler.run(runs.get(SecurityPrincipalSupport.current().userId(), id)));
    }
    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String id, @RequestParam(defaultValue = "0") long after) {
        return streams.subscribe(SecurityPrincipalSupport.current().userId(), id, after);
    }
    @PostMapping("/{id}/cancel")
    public Result<AiRunRes> cancel(@PathVariable String id) {
        return Result.ok(AiRunWebAssembler.run(runs.cancel(SecurityPrincipalSupport.current().userId(), id)));
    }
    @PostMapping("/{id}/resume")
    public Result<AiRunRes> resume(@PathVariable String id) {
        var principal = SecurityPrincipalSupport.current();
        return Result.ok(AiRunWebAssembler.run(runs.resume(principal.userId(), id, principal.permissions())));
    }
    @PostMapping("/{id}/frontend-tools/{toolCallId}")
    public Result<Void> frontendResult(@PathVariable String id, @PathVariable String toolCallId,
                                       @Valid @RequestBody AiFrontendToolResultRequest request) {
        var principal = SecurityPrincipalSupport.current();
        runs.frontendResult(principal.userId(), id, toolCallId, request.ok(), request.result(), request.error(), principal.permissions());
        return Result.ok();
    }
    @PostMapping("/{id}/interactions/{requestId}")
    public Result<AiRunRes> answer(@PathVariable String id, @PathVariable String requestId,
                                  @Valid @RequestBody online.yudream.base.interfaces.platform.ai.request.AiInteractionAnswerRequest request) {
        var principal = SecurityPrincipalSupport.current();
        return Result.ok(AiRunWebAssembler.run(runs.respond(principal.userId(), id, requestId,
                request.approved(), request.output(), principal.permissions())));
    }
}
