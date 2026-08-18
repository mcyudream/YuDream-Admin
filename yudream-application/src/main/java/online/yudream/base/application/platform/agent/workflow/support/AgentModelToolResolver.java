package online.yudream.base.application.platform.agent.workflow.support;

import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;

import java.util.LinkedHashSet;
import java.util.List;

/** Resolves the selected tools of one model node into native model callbacks. */
public final class AgentModelToolResolver {
    private final AgentToolExecutor toolExecutor;

    public AgentModelToolResolver(AgentToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }

    public List<AiAgentTool> resolve(
            List<String> nodeToolCodes,
            AgentApplication application,
            AgentRunCmd command
    ) {
        AgentToolExecutor.PermissionSnapshot permissionSnapshot = toolExecutor.capturePermissionSnapshot(command);
        LinkedHashSet<String> orderedCodes = new LinkedHashSet<>();
        if (nodeToolCodes != null) {
            nodeToolCodes.stream()
                    .filter(code -> code != null && !code.isBlank())
                    .map(String::trim)
                    .forEach(orderedCodes::add);
        }
        List<AiAgentTool> resolved = new java.util.ArrayList<>(
                orderedCodes.stream()
                        .map(code -> toolExecutor.resolve(code, application, permissionSnapshot))
                        .toList()
        );
        if (command != null && command.isRuntimeToolCallingEnabled() && command.getRuntimeToolCodes() != null) {
            command.getRuntimeToolCodes().stream()
                    .filter(code -> code != null && !code.isBlank())
                    .map(String::trim)
                    .filter(code -> !orderedCodes.contains(code))
                    .map(code -> toolExecutor.resolveRuntime(code, permissionSnapshot))
                    .filter(tool -> tool != null)
                    .forEach(resolved::add);
        }
        return List.copyOf(resolved);
    }
}
