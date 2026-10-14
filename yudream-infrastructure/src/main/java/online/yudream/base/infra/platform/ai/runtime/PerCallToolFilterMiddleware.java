package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.model.ToolSchema;
import online.yudream.base.domain.platform.agent.service.AgentPermissionGateway;
import online.yudream.base.domain.platform.agent.valobj.AgentToolPermissions;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.Function;

/**
 * 按次调用工具过滤中间件（schema 层权限控制）：
 * ① 本次调用禁用工具时清空工具清单；② 开启时按权限快照过滤，
 * 用户无对应 RBAC 权限的工具不进入模型视野（执行层仍有 fail-closed 复核）。
 */
public class PerCallToolFilterMiddleware implements MiddlewareBase {

    private final AgentPermissionGateway permissionGateway;
    private final online.yudream.base.domain.platform.agent.service.AgentToolExecutionGuard nativeGuard;
    private static final java.util.Set<String> NATIVE_READ_TOOLS = java.util.Set.of(
            "read_file", "list_files", "grep_files", "glob_files", "memory_search", "memory_get", "session_search", "load_skill");

    public PerCallToolFilterMiddleware(AgentPermissionGateway permissionGateway) {
        this(permissionGateway, online.yudream.base.domain.platform.agent.service.AgentToolExecutionGuard.ALLOW_ALL);
    }

    public PerCallToolFilterMiddleware(AgentPermissionGateway permissionGateway,
            online.yudream.base.domain.platform.agent.service.AgentToolExecutionGuard guard) {
        this.permissionGateway = permissionGateway;
        this.nativeGuard = guard == null ? online.yudream.base.domain.platform.agent.service.AgentToolExecutionGuard.ALLOW_ALL : guard.capture();
    }

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent,
            RuntimeContext runtimeContext,
            ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next
    ) {
        AgentToolCatalog catalog = runtimeContext == null ? null : runtimeContext.get(AgentToolCatalog.class);
        if (catalog == null) {
            return next.apply(input);
        }
        if (!catalog.enabled()) {
            report(runtimeContext, List.of());
            return next.apply(new ReasoningInput(input.messages(), List.of(), input.options()));
        }
        AgentToolPermissions permissions = runtimeContext.get(AgentToolPermissions.class);
        List<String> codes = permissions == null ? List.of() : permissions.codes();
        boolean explicit = permissions != null && permissions.explicitContext();
        java.util.Set<String> allowed = catalog.allowed();
        List<ToolSchema> visible = input.tools().stream()
                .filter(schema -> allowed.isEmpty() || allowed.contains(schema.getName()))
                .filter(schema -> hasPermission(catalog.permissions().get(schema.getName()), codes, explicit))
                .toList();
        report(runtimeContext, visible.stream().map(ToolSchema::getName).toList());
        return next.apply(new ReasoningInput(input.messages(), visible, input.options()));
    }

    private void report(RuntimeContext context, List<String> names) {
        AgentToolSchemaObserver observer = context.get(AgentToolSchemaObserver.class);
        if (observer != null) observer.report(names);
    }

    @Override
    public Flux<AgentEvent> onActing(Agent agent, RuntimeContext context, ActingInput input,
                                     Function<ActingInput, Flux<AgentEvent>> next) {
        AgentToolCatalog catalog = context == null ? null : context.get(AgentToolCatalog.class);
        if (catalog == null) return next.apply(input);
        for (var call : input.toolCalls()) {
            String permission = catalog.permissions().get(call.getName());
            if (!catalog.enabled() || !catalog.allowed().isEmpty() && !catalog.allowed().contains(call.getName())) {
                String message = "当前会话未授权 AI 工具：" + call.getName();
                failure(context, call, permission, message, true);
                return Flux.error(new online.yudream.base.domain.common.exception.BizException(message));
            }
            // 宿主/SPI adapter 已包住完整执行；框架内置工具也必须在真实执行前复核已捕获沙箱。
            if (!catalog.permissions().containsKey(call.getName())) {
                try {
                    nativeGuard.check(call.getName(), NATIVE_READ_TOOLS.contains(call.getName())
                            ? online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk.READ
                            : online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk.WRITE, permission);
                } catch (RuntimeException rejected) {
                    failure(context, call, permission, rejected.getMessage(), rejected instanceof online.yudream.base.domain.common.exception.BizException);
                    return Flux.error(rejected);
                }
            }
        }
        return next.apply(input);
    }

    private void failure(RuntimeContext context, io.agentscope.core.message.ToolUseBlock call, String permission, String message, boolean denied) {
        var collector = context.get(HarnessToolCollector.class);
        if (collector != null) collector.record(new online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult(
                call.getName(), denied ? "denied" : "error", permission, message,
                java.util.Map.of("_toolCallId", call.getId(), "_nativeToolState", denied ? "DENIED" : "ERROR")));
    }

    private boolean hasPermission(String permissionCode, List<String> codes, boolean explicit) {
        return permissionGateway.hasPermission(permissionCode, codes, explicit);
    }
}
