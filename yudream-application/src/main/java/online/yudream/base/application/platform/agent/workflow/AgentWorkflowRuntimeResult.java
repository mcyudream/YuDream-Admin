package online.yudream.base.application.platform.agent.workflow;

import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;

import java.util.List;

public record AgentWorkflowRuntimeResult(
        String content,
        String reasoning,
        List<AiAgentToolResult> toolResults,
        AiUsage usage
) {
    public AgentWorkflowRuntimeResult {
        reasoning = reasoning == null ? "" : reasoning;
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
        usage = usage == null ? AiUsage.empty() : usage;
    }

    public AgentWorkflowRuntimeResult(String content, List<AiAgentToolResult> toolResults) {
        this(content, "", toolResults, AiUsage.empty());
    }

    public AgentWorkflowRuntimeResult(String content, String reasoning, List<AiAgentToolResult> toolResults) {
        this(content, reasoning, toolResults, AiUsage.empty());
    }
}
