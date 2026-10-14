package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.plugin.spi.system.ai.PluginAiTool;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolCall;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolDescriptor;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolRisk;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 插件工具（SPI {@link PluginAiTool}）到 AgentScope {@link ToolBase} 的适配。
 * 插件执行上下文（触发器、白名单、写授权、权限码）在装配期绑定；READ 默认放行，
 * WRITE/DESTRUCTIVE 需调用方在 grantedWriteToolNames 中逐个点名，DESTRUCTIVE 额外要求用户确认。
 */
@Slf4j
public class PluginAiToolAdapter extends ToolBase {

    private final PluginAiTool delegate;
    private final PluginAiToolDescriptor descriptor;
    private final online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext context;
    private final online.yudream.base.domain.platform.agent.service.AgentToolExecutionGuard guard;

    public PluginAiToolAdapter(
            PluginAiTool delegate,
            online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext context
    ) {
        this(delegate, context, online.yudream.base.domain.platform.agent.service.AgentToolExecutionGuard.ALLOW_ALL);
    }

    public PluginAiToolAdapter(PluginAiTool delegate,
            online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext context,
            online.yudream.base.domain.platform.agent.service.AgentToolExecutionGuard guard) {
        super(ToolBase.builder()
                .name(ToolSchemas.safeName(delegate.descriptor().name()))
                .description(delegate.descriptor().description())
                .inputSchema(ToolSchemas.normalizeSimple(delegate.descriptor().inputSchema()))
                .readOnly(delegate.descriptor().risk() == PluginAiToolRisk.READ)
                .concurrencySafe(delegate.descriptor().risk() == PluginAiToolRisk.READ));
        this.delegate = delegate;
        this.descriptor = delegate.descriptor();
        this.context = context;
        this.guard = guard == null ? online.yudream.base.domain.platform.agent.service.AgentToolExecutionGuard.ALLOW_ALL : guard.capture();
    }

    public String platformToolName() {
        return descriptor.name();
    }

    boolean requiresPersistentInteractions() { return descriptor.requiresConfirmation() || descriptor.risk() == PluginAiToolRisk.DESTRUCTIVE; }

    /**
     * 装配期过滤条件与旧网关一致：READ 默认可用，WRITE/DESTRUCTIVE 需显式点名授权；
     * 允许清单、触发器与权限码一并校验。
     */
    public boolean granted() {
        boolean riskOk = descriptor.risk() == PluginAiToolRisk.READ || context.grantsWriteTool(descriptor.name());
        return riskOk
                && context.allowsTool(descriptor.name())
                && descriptor.allowedTriggers().contains(context.trigger())
                && context.hasPermission(descriptor.permissionCode());
    }

    @Override
    public Mono<PermissionDecision> checkPermissions(Map<String, Object> toolInput, PermissionContextState state) {
        if (descriptor.risk() == PluginAiToolRisk.DESTRUCTIVE || descriptor.requiresConfirmation()) {
            return Mono.just(PermissionDecision.builder()
                    .behavior(io.agentscope.core.permission.PermissionBehavior.ASK)
                    .message("高风险插件工具需要用户确认：" + descriptor.title())
                    .decisionReason("safety: plugin tool requires user confirmation").build());
        }
        return Mono.just(PermissionDecision.passthrough("grants checked at assembly and execution"));
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        var catalog = param.getRuntimeContext() == null ? null : param.getRuntimeContext().get(AgentToolCatalog.class);
        if (!granted() || catalog != null && (!catalog.enabled()
                || !catalog.allowed().isEmpty() && !catalog.allowed().contains(getName()))) {
            log.info("[YuDreamAdmin] plugin AI tool denied: tool={}, risk={}, trigger={}",
                    descriptor.name(), descriptor.risk(), context.trigger());
            return Mono.just(failure(param, ToolResultState.DENIED, "当前用户无权调用 AI 工具：" + descriptor.title()));
        }
        try {
            var value = guard.execute(descriptor.name(), descriptor.risk() == PluginAiToolRisk.READ
                    ? online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk.READ
                    : online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk.WRITE,
                    descriptor.permissionCode(), () -> delegate.execute(context, new PluginAiToolCall(descriptor.name(), inputOf(param))));
            var result = new online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult(
                    descriptor.name(), value.action(), descriptor.permissionCode(), value.message(), value.payload());
            io.agentscope.core.agent.RuntimeContext runtimeContext = param.getRuntimeContext();
            if (runtimeContext != null) {
                HarnessToolCollector collector = runtimeContext.get(HarnessToolCollector.class);
                if (collector != null) {
                    collector.record(identified(param, result, ToolResultState.SUCCESS));
                }
            }
            return Mono.just(result(param, ToolResultState.SUCCESS, ToolSchemas.resultJson(result)));
        } catch (Exception e) {
            log.debug("plugin AI tool execution failed: tool={}", descriptor.name(), e);
            return Mono.just(failure(param, ToolResultState.ERROR, "工具执行失败：" + e.getMessage()));
        }
    }

    private ToolResultBlock failure(ToolCallParam param, ToolResultState state, String message) {
        var failure = new online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult(descriptor.name(),
                state == ToolResultState.DENIED ? "denied" : "error", descriptor.permissionCode(), message, Map.of());
        var context = param.getRuntimeContext();
        if (context != null && context.get(HarnessToolCollector.class) != null) {
            context.get(HarnessToolCollector.class).record(identified(param, failure, state));
        }
        return result(param, state, message);
    }

    private online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult identified(ToolCallParam param,
            online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult result, ToolResultState state) {
        var payload = new java.util.LinkedHashMap<String, Object>(result.payload() == null ? Map.of() : result.payload());
        payload.put("_toolCallId", param.getToolUseBlock().getId()); payload.put("_nativeToolState", state.name());
        return new online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult(result.toolName(), result.action(), result.permissionCode(), result.message(), payload);
    }

    private static Map<String, Object> inputOf(ToolCallParam param) {
        return param.getInput() == null ? Map.of() : param.getInput();
    }

    private static ToolResultBlock result(ToolCallParam param, ToolResultState state, String message) {
        return ToolResultBlock.builder()
                .id(param.getToolUseBlock().getId())
                .name(param.getToolUseBlock().getName())
                .output(TextBlock.builder().text(message).build())
                .state(state)
                .build();
    }
}
