package online.yudream.base.application.platform.agent.dto;

public record AgentDebugEventDTO(
        String nodeId,
        String nodeKind,
        String nodeTitle,
        String status,
        String message
) {
}
