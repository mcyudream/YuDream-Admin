package online.yudream.base.application.platform.agent.workflow.support;

import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class AgentWorkflowRunState {
    private final AgentApplication application;
    private final AgentRunCmd command;
    private final Map<String, String> aiConfig;
    private final Set<String> systemToolCodes;
    private final Consumer<String> onDelta;
    private final Consumer<AiAgentToolResult> onTool;
    private final List<AiAgentToolResult> toolResults = new ArrayList<>();

    public AgentWorkflowRunState(
            AgentApplication application,
            AgentRunCmd command,
            Map<String, String> aiConfig,
            Set<String> systemToolCodes,
            Consumer<String> onDelta,
            Consumer<AiAgentToolResult> onTool
    ) {
        this.application = application;
        this.command = command;
        this.aiConfig = aiConfig == null ? Map.of() : Map.copyOf(aiConfig);
        this.systemToolCodes = systemToolCodes == null ? Set.of() : Set.copyOf(systemToolCodes);
        this.onDelta = onDelta;
        this.onTool = onTool;
    }

    public AgentApplication application() {
        return application;
    }

    public AgentRunCmd command() {
        return command;
    }

    public Map<String, String> aiConfig() {
        return aiConfig;
    }

    public Set<String> systemToolCodes() {
        return systemToolCodes;
    }

    public List<AiAgentToolResult> toolResults() {
        return List.copyOf(toolResults);
    }

    public void emitDelta(String delta) {
        if (onDelta != null && delta != null && !delta.isEmpty()) {
            onDelta.accept(delta);
        }
    }

    public void addToolResult(AiAgentToolResult result) {
        if (result == null || toolResults.contains(result)) {
            return;
        }
        toolResults.add(result);
        if (onTool != null) {
            onTool.accept(result);
        }
    }
}
