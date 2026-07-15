package online.yudream.base.application.platform.agent.workflow;

import com.fasterxml.jackson.databind.JsonNode;

public record AgentWorkflowNode(String id, String kind, String title, JsonNode data) {
}
