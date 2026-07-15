package online.yudream.base.interfaces.platform.agent.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.query.AgentPageQuery;
import online.yudream.base.application.platform.agent.query.AgentToolPageQuery;
import online.yudream.base.application.platform.agent.service.AgentAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.agent.assembler.AgentWebAssembler;
import online.yudream.base.interfaces.platform.agent.request.AgentApplicationSaveRequest;
import online.yudream.base.interfaces.platform.agent.request.AgentRunRequest;
import online.yudream.base.interfaces.platform.agent.request.AgentToolSaveRequest;
import online.yudream.base.interfaces.platform.agent.res.AgentApplicationRes;
import online.yudream.base.interfaces.platform.agent.res.AgentCatalogRes;
import online.yudream.base.interfaces.platform.agent.res.AgentDebugEventRes;
import online.yudream.base.interfaces.platform.agent.res.AgentModelRes;
import online.yudream.base.interfaces.platform.agent.res.AgentRunRes;
import online.yudream.base.interfaces.platform.agent.res.AgentToolRes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/platform/agents")
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.agent", name = "enabled", havingValue = "true")
public class AgentController {
    private final AgentAppService agentAppService;

    @Value("${yudream.platform.ai.client.sse-timeout:30m}")
    private Duration sseTimeout;

    @GetMapping
    @PermissionRegister(code = "platform:agent:view", name = "查看 Agent 应用", module = "平台能力", desc = "查看 Agent 应用列表")
    public Result<PageResult<AgentApplicationRes>> page(AgentPageQuery query) { return Result.ok(AgentWebAssembler.toApplicationPage(agentAppService.page(query))); }
    @GetMapping("/available")
    @PermissionRegister(code = "platform:agent:view", name = "查看可用 Agent 应用", module = "平台能力", desc = "查看持久化与插件运行时 Agent 应用")
    public Result<List<AgentApplicationRes>> available() { return Result.ok(agentAppService.publishedApplications().stream().map(AgentWebAssembler::toRes).toList()); }
    @GetMapping("/runtime/{code}")
    @PermissionRegister(code = "platform:agent:view", name = "查看插件 Agent 编排", module = "平台能力", desc = "查看当前插件提供的运行时 Agent 编排")
    public Result<AgentApplicationRes> runtime(@PathVariable String code) { return Result.ok(AgentWebAssembler.toRes(agentAppService.runtimeApplication(code))); }
    @GetMapping("/{id}")
    @PermissionRegister(code = "platform:agent:view", name = "查看 Agent 应用详情", module = "平台能力", desc = "查看 Agent 编排详情")
    public Result<AgentApplicationRes> detail(@PathVariable Long id) { return Result.ok(AgentWebAssembler.toRes(agentAppService.detail(id))); }
    @GetMapping("/models")
    @PermissionRegister(code = "platform:agent:view", name = "查看 Agent 模型", module = "平台能力", desc = "查看 Agent 节点可用模型")
    public Result<List<AgentModelRes>> models() { return Result.ok(agentAppService.models().stream().map(AgentWebAssembler::toRes).toList()); }
    @GetMapping("/catalog")
    @PermissionRegister(code = "platform:agent:view", name = "查看 Agent 编排目录", module = "平台能力", desc = "查看 Agent 节点可用模型与知识空间")
    public Result<AgentCatalogRes> catalog() { return Result.ok(AgentWebAssembler.toRes(agentAppService.catalog())); }
    @PostMapping
    @PermissionRegister(code = "platform:agent:edit", name = "新建 Agent 应用", module = "平台能力", desc = "新建 Agent 应用")
    public Result<AgentApplicationRes> create(@Valid @RequestBody AgentApplicationSaveRequest request) { return Result.ok(AgentWebAssembler.toRes(agentAppService.save(AgentWebAssembler.toCmd(request)))); }
    @PutMapping("/{id}")
    @PermissionRegister(code = "platform:agent:edit", name = "编辑 Agent 应用", module = "平台能力", desc = "编辑 Agent 编排")
    public Result<AgentApplicationRes> update(@PathVariable Long id, @Valid @RequestBody AgentApplicationSaveRequest request) { return Result.ok(AgentWebAssembler.toRes(agentAppService.save(AgentWebAssembler.toCmd(id, request)))); }
    @PostMapping("/{id}/publish")
    @PermissionRegister(code = "platform:agent:publish", name = "发布 Agent 应用", module = "平台能力", desc = "发布 Agent 应用")
    public Result<Void> publish(@PathVariable Long id) { agentAppService.publish(id); return Result.ok(); }
    @DeleteMapping("/{id}")
    @PermissionRegister(code = "platform:agent:delete", name = "删除 Agent 应用", module = "平台能力", desc = "删除 Agent 应用")
    public Result<Void> delete(@PathVariable Long id) { agentAppService.deleteApplication(id); return Result.ok(); }
    @PostMapping("/{id}/run")
    @PermissionRegister(code = "platform:agent:run", name = "运行 Agent 应用", module = "平台能力", desc = "运行 Agent 应用")
    public Result<AgentRunRes> run(@PathVariable Long id, @Valid @RequestBody AgentRunRequest request) { return Result.ok(AgentWebAssembler.toRes(agentAppService.run(AgentWebAssembler.toRunCmd(id, request)))); }

    @PostMapping(value = "/{id}/debug/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PermissionRegister(code = "platform:agent:edit", name = "调试 Agent 应用", module = "平台能力", desc = "流式调试 Agent 工作流")
    public SseEmitter debug(@PathVariable Long id, @Valid @RequestBody AgentRunRequest request) {
        SseEmitter emitter = new SseEmitter(sseTimeout.toMillis());
        String runId = UUID.randomUUID().toString();
        CompletableFuture.runAsync(() -> {
            try {
                send(emitter, AgentWebAssembler.toDebugRunStarted(runId));
                var result = agentAppService.debug(
                        AgentWebAssembler.toRunCmd(id, request),
                        event -> send(emitter, AgentWebAssembler.toDebugNodeEvent(runId, event)),
                        delta -> send(emitter, AgentWebAssembler.toDebugTextChunk(runId, delta)),
                        tool -> send(emitter, AgentWebAssembler.toDebugToolResult(runId, tool))
                );
                send(emitter, AgentWebAssembler.toDebugRunFinished(runId, result));
                emitter.complete();
            } catch (Exception e) {
                send(emitter, AgentWebAssembler.toDebugRunError(runId, e.getMessage()));
                emitter.complete();
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, AgentDebugEventRes event) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(event.getType()).data(event));
            }
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    @GetMapping("/tools")
    @PermissionRegister(code = "platform:agent:tool:view", name = "查看 Agent 工具", module = "平台能力", desc = "查看自定义 Agent 工具")
    public Result<PageResult<AgentToolRes>> tools(AgentToolPageQuery query) { return Result.ok(AgentWebAssembler.toToolPage(agentAppService.pageTools(query))); }
    @GetMapping("/tools/system")
    @PermissionRegister(code = "platform:agent:tool:view", name = "查看系统 Agent 工具", module = "平台能力", desc = "查看系统提供的 Agent 工具")
    public Result<List<Map<String, Object>>> systemTools() { return Result.ok(agentAppService.systemTools()); }
    @GetMapping("/tools/{id}")
    @PermissionRegister(code = "platform:agent:tool:view", name = "查看 Agent 工具详情", module = "平台能力", desc = "查看 Python 工具详情")
    public Result<AgentToolRes> toolDetail(@PathVariable Long id) { return Result.ok(AgentWebAssembler.toRes(agentAppService.toolDetail(id))); }
    @PostMapping("/tools")
    @PermissionRegister(code = "platform:agent:tool:edit", name = "新建 Python Agent 工具", module = "平台能力", desc = "新建 Python 工具")
    public Result<AgentToolRes> createTool(@Valid @RequestBody AgentToolSaveRequest request) { return Result.ok(AgentWebAssembler.toRes(agentAppService.saveTool(AgentWebAssembler.toCmd(request)))); }
    @PutMapping("/tools/{id}")
    @PermissionRegister(code = "platform:agent:tool:edit", name = "编辑 Python Agent 工具", module = "平台能力", desc = "编辑 Python 工具")
    public Result<AgentToolRes> updateTool(@PathVariable Long id, @Valid @RequestBody AgentToolSaveRequest request) { return Result.ok(AgentWebAssembler.toRes(agentAppService.saveTool(AgentWebAssembler.toCmd(id, request)))); }
    @DeleteMapping("/tools/{id}")
    @PermissionRegister(code = "platform:agent:tool:delete", name = "删除 Python Agent 工具", module = "平台能力", desc = "删除 Python 工具")
    public Result<Void> deleteTool(@PathVariable Long id) { agentAppService.deleteTool(id); return Result.ok(); }
}
