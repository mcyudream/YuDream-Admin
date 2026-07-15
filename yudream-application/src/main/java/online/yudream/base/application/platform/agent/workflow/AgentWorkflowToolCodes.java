package online.yudream.base.application.platform.agent.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Computes the application authorization boundary from executable workflow nodes.
 * Client-provided application tool lists are intentionally not an input here.
 */
public final class AgentWorkflowToolCodes {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> MODEL_KINDS = Set.of("llm", "extract", "classify", "vision", "understand");

    private AgentWorkflowToolCodes() {
    }

    public static List<String> derive(String workflowJson) {
        if (workflowJson == null || workflowJson.isBlank()) {
            throw new AgentWorkflowDefinitionException("工作流 JSON 不能为空");
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(workflowJson);
            if (!root.isObject() || !root.path("nodes").isArray()) {
                throw new AgentWorkflowDefinitionException("工作流必须包含 nodes 数组");
            }
            LinkedHashSet<String> result = new LinkedHashSet<>();
            for (JsonNode node : root.path("nodes")) {
                JsonNode data = node.path("data");
                if (!data.isObject()) {
                    continue;
                }
                String kind = data.path("kind").asText("").trim();
                if (MODEL_KINDS.contains(kind)) {
                    addArrayCodes(data.path("toolCodes"), result);
                } else if ("tool".equals(kind)) {
                    addCode(data.path("toolCode"), result);
                }
            }
            return List.copyOf(result);
        } catch (AgentWorkflowDefinitionException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AgentWorkflowDefinitionException("工作流 JSON 格式无效", exception);
        }
    }

    public static List<String> derive(AgentWorkflowGraph graph) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (AgentWorkflowNode node : graph.topologicalOrder()) {
            if (MODEL_KINDS.contains(node.kind())) {
                addArrayCodes(node.data().path("toolCodes"), result);
            } else if ("tool".equals(node.kind())) {
                addCode(node.data().path("toolCode"), result);
            }
        }
        return List.copyOf(result);
    }

    private static void addArrayCodes(JsonNode values, LinkedHashSet<String> target) {
        if (!values.isArray()) {
            return;
        }
        values.forEach(value -> addCode(value, target));
    }

    private static void addCode(JsonNode value, LinkedHashSet<String> target) {
        if (value == null || !value.isTextual()) {
            return;
        }
        String code = value.asText().trim();
        if (!code.isEmpty()) {
            target.add(code);
        }
    }
}
