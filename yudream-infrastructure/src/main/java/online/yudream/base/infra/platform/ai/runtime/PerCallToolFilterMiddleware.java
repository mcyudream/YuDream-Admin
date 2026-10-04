package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
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

    public PerCallToolFilterMiddleware(AgentPermissionGateway permissionGateway) {
        this.permissionGateway = permissionGateway;
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
            return next.apply(new ReasoningInput(input.messages(), List.of(), input.options()));
        }
        AgentToolPermissions permissions = runtimeContext.get(AgentToolPermissions.class);
        List<String> codes = permissions == null ? List.of() : permissions.codes();
        boolean explicit = permissions != null && permissions.explicitContext();
        List<ToolSchema> visible = input.tools().stream()
                .filter(schema -> hasPermission(catalog.permissions().get(schema.getName()), codes, explicit))
                .toList();
        return next.apply(new ReasoningInput(input.messages(), visible, input.options()));
    }

    private boolean hasPermission(String permissionCode, List<String> codes, boolean explicit) {
        return permissionGateway.hasPermission(permissionCode, codes, explicit);
    }
}
