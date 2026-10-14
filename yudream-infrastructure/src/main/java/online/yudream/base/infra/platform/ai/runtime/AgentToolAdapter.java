package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.agent.RuntimeContext;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import online.yudream.base.domain.platform.agent.service.AgentPermissionGateway;
import online.yudream.base.domain.platform.agent.service.AgentToolExecutionGuard;
import online.yudream.base.domain.platform.agent.valobj.AgentToolPermissions;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.AiExternalAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 平台系统工具（domain {@link AiAgentTool}）到 AgentScope {@link ToolBase} 的适配。
 * <p>
 * 权限控制分四层：
 * ① schema 层——无权限的工具不进入模型可见的工具清单（由装配层按快照过滤）；
 * ② 规则层——checkPermissions 对缺失 RBAC 授权返回 passthrough、由规则/模式裁决；
 * ③ 执行层——callAsync 以 RuntimeContext 中的权限快照复核（fail-closed），不可绕过；
 * ④ 守卫层——AgentToolExecutionGuard（如 QQ 沙箱只读）在真实执行前最后把关。
 * 数据控制：工具结果统一 JSON 序列化，超大结果由框架 toolResultEviction 卸载到工作区。
 */
@Slf4j
public class AgentToolAdapter extends ToolBase {

    private final AiAgentTool delegate;
    private final AiAgentToolDescriptor descriptor;
    private final AgentPermissionGateway permissionGateway;
    private final AgentToolExecutionGuard executionGuard;

    public AgentToolAdapter(
            AiAgentTool delegate,
            AgentPermissionGateway permissionGateway,
            AgentToolExecutionGuard executionGuard
    ) {
        this(delegate, permissionGateway, executionGuard, ToolSchemas.safeName(delegate.descriptor().name()));
    }

    /**
     * alias 用于同平台名多实现（如 wiki.search 管理端/公开端）的模型侧名字消解；
     * 平台侧关联仍用 descriptor.name()。
     */
    public AgentToolAdapter(
            AiAgentTool delegate,
            AgentPermissionGateway permissionGateway,
            AgentToolExecutionGuard executionGuard,
            String alias
    ) {
        super(ToolBase.builder()
                .name(alias)
                .description(delegate.descriptor().description())
                .inputSchema(ToolSchemas.normalize(delegate.descriptor()))
                .readOnly(delegate.risk() == AgentToolRisk.READ)
                .concurrencySafe(delegate.risk() == AgentToolRisk.READ));
        this.delegate = delegate;
        this.descriptor = delegate.descriptor();
        this.permissionGateway = permissionGateway;
        this.executionGuard = executionGuard == null ? AgentToolExecutionGuard.ALLOW_ALL : executionGuard.capture();
    }

    public String platformToolName() {
        return descriptor.name();
    }

    public String permissionCode() {
        return descriptor.permissionCode();
    }

    public AgentToolRisk risk() {
        return delegate.risk();
    }

    boolean requiresPersistentInteractions() { return delegate.requiresConfirmation() || delegate instanceof AiExternalAgentTool; }

    @Override
    public Mono<PermissionDecision> checkPermissions(Map<String, Object> toolInput, PermissionContextState context) {
        if (!delegate.requiresConfirmation() && delegate.risk() == AgentToolRisk.READ) {
            // Schema 和执行阶段仍以可信快照复核；普通只读动作无需每次人工批准。
            return Mono.just(PermissionDecision.allow("只读工具将在执行前复核用户权限"));
        }
        return Mono.just(PermissionDecision.builder()
                .behavior(io.agentscope.core.permission.PermissionBehavior.ASK)
                .message("工具需要用户确认：" + descriptor.title())
                .decisionReason("safety: platform write tool requires user confirmation").build());
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        if (!hasPermission(param.getRuntimeContext())) {
            log.info("[YuDreamAdmin] AI tool denied by permission snapshot: tool={}, permissionCode={}",
                    descriptor.name(), descriptor.permissionCode());
            return Mono.just(failure(param, ToolResultState.DENIED,
                    "当前用户无权调用 AI 工具：" + descriptor.title()));
        }
        try {
            return Mono.just(executionGuard.execute(descriptor.name(), delegate.risk(), descriptor.permissionCode(),
                    () -> executeDelegate(param)));
        } catch (BizException e) {
            return Mono.just(failure(param, ToolResultState.DENIED, e.getMessage()));
        }
    }

    private ToolResultBlock executeDelegate(ToolCallParam param) {
        try {
            if (delegate instanceof AiExternalAgentTool external) {
                AiAgentToolResult request = external.prepare(param.getToolUseBlock().getId(),
                        new AiAgentToolCall(descriptor.name(), inputOf(param)));
                recordIfPresent(param, identified(param, request, ToolResultState.RUNNING));
                return ToolResultBlock.suspended(param.getToolUseBlock(),
                        new io.agentscope.core.tool.ToolSuspendException("等待真实外部执行结果"));
            }
            AiAgentToolResult result = delegate.execute(new AiAgentToolCall(descriptor.name(), inputOf(param)));
            recordIfPresent(param, identified(param, result, ToolResultState.SUCCESS));
            return result(param, ToolResultState.SUCCESS, ToolSchemas.resultJson(result));
        } catch (BizException e) {
            return failure(param, ToolResultState.ERROR, e.getMessage());
        } catch (Exception e) {
            log.debug("AI tool execution failed: tool={}", descriptor.name(), e);
            return failure(param, ToolResultState.ERROR, "工具执行失败：" + e.getMessage());
        }
    }

    private ToolResultBlock failure(ToolCallParam param, ToolResultState state, String message) {
        AiAgentToolResult failure = new AiAgentToolResult(descriptor.name(), state == ToolResultState.DENIED ? "denied" : "error",
                descriptor.permissionCode(), message, Map.of());
        recordIfPresent(param, identified(param, failure, state));
        return result(param, state, message);
    }

    private AiAgentToolResult identified(ToolCallParam param, AiAgentToolResult result, ToolResultState state) {
        var payload = new java.util.LinkedHashMap<String, Object>(result.payload() == null ? Map.of() : result.payload());
        payload.put("_toolCallId", param.getToolUseBlock().getId());
        payload.put("_nativeToolState", state.name());
        return new AiAgentToolResult(result.toolName(), result.action(), result.permissionCode(), result.message(), payload);
    }

    private void recordIfPresent(ToolCallParam param, AiAgentToolResult result) {
        RuntimeContext runtimeContext = param.getRuntimeContext();
        if (runtimeContext == null) {
            return;
        }
        HarnessToolCollector collector = runtimeContext.get(HarnessToolCollector.class);
        if (collector != null) {
            collector.record(result);
        }
    }

    private boolean hasPermission(RuntimeContext runtimeContext) {
        AgentToolCatalog catalog = runtimeContext == null ? null : runtimeContext.get(AgentToolCatalog.class);
        if (catalog != null && (!catalog.enabled() || !catalog.allowed().isEmpty() && !catalog.allowed().contains(getName()))) return false;
        AgentToolPermissions permissions = runtimeContext == null
                ? null
                : runtimeContext.get(AgentToolPermissions.class);
        List<String> codes = permissions == null ? List.of() : permissions.codes();
        boolean explicit = permissions != null && permissions.explicitContext();
        return permissionGateway.hasPermission(descriptor.permissionCode(), codes, explicit);
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
