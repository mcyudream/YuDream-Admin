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
        if (nodeToolCodes == null || nodeToolCodes.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> orderedCodes = new LinkedHashSet<>();
        nodeToolCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .forEach(orderedCodes::add);
        return orderedCodes.stream()
                .map(code -> toolExecutor.resolve(code, application, command))
                .toList();
    }
}
