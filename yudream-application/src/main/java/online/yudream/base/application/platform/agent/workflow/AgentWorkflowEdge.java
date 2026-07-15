package online.yudream.base.application.platform.agent.workflow;

public record AgentWorkflowEdge(
        String id,
        String source,
        String target,
        String sourceHandle,
        String targetHandle
) {
}
