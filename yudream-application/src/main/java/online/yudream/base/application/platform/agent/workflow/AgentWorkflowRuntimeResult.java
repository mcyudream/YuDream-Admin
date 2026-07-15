package online.yudream.base.application.platform.agent.workflow;

import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;

import java.util.List;

public record AgentWorkflowRuntimeResult(String content, List<AiAgentToolResult> toolResults) {
    public AgentWorkflowRuntimeResult {
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
    }
}
