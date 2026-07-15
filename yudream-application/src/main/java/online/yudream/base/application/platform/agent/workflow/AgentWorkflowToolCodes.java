package online.yudream.base.application.platform.agent.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Computes the application authorization boundary from executable workflow nodes.
 * Client-provided application tool lists are intentionally not an input here.
 */
public final class AgentWorkflowToolCodes {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> TOOL_MODEL_KINDS = Set.of("llm", "extract", "classify", "vision");
    private static final String LEGACY_UNDERSTAND = "understand";

    private AgentWorkflowToolCodes() {
    }

    public static List<String> derive(String workflowJson) {
        return normalize(workflowJson).toolCodes();
    }

    /**
     * NONE is a durable state, so stale selections are removed before persisting.
     * Legacy understand nodes never support native model tool calling.
     */
    public static NormalizedWorkflow normalize(String workflowJson) {
        if (workflowJson == null || workflowJson.isBlank()) {
            throw new AgentWorkflowDefinitionException("工作流 JSON 不能为空");
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(workflowJson);
            if (!root.isObject() || !root.path("nodes").isArray()) {
                throw new AgentWorkflowDefinitionException("工作流必须包含 nodes 数组");
            }
            boolean changed = false;
            boolean declaresTools = false;
            for (JsonNode node : root.path("nodes")) {
                JsonNode data = node.path("data");
                if (!data.isObject()) {
                    continue;
                }
                String kind = kind(data);
                if (LEGACY_UNDERSTAND.equals(kind)) {
                    changed |= clearUnderstandToolConfig((ObjectNode) data);
                } else if (TOOL_MODEL_KINDS.contains(kind)) {
                    changed |= migrateLegacyActiveMode((ObjectNode) data);
                    declaresTools |= declaresNodeToolConfig(data);
                    if (isToolModeNone(data)) {
                        changed |= clearToolCodes((ObjectNode) data);
                    }
                } else if ("tool".equals(kind)) {
                    declaresTools = true;
                }
            }
            return new NormalizedWorkflow(
                    changed ? OBJECT_MAPPER.writeValueAsString(root) : workflowJson,
                    deriveRoot(root),
                    declaresTools
            );
        } catch (AgentWorkflowDefinitionException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AgentWorkflowDefinitionException("工作流 JSON 格式无效", exception);
        }
    }

    public static List<String> derive(AgentWorkflowGraph graph) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (AgentWorkflowNode node : graph.topologicalOrder()) {
            if (TOOL_MODEL_KINDS.contains(node.kind()) && !isToolModeNone(node.data())) {
                addArrayCodes(node.data().path("toolCodes"), result);
            } else if ("tool".equals(node.kind())) {
                addCode(node.data().path("toolCode"), result);
            }
        }
        return List.copyOf(result);
    }

    private static List<String> deriveRoot(JsonNode root) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (JsonNode node : root.path("nodes")) {
            JsonNode data = node.path("data");
            if (TOOL_MODEL_KINDS.contains(kind(data)) && !isToolModeNone(data)) {
                addArrayCodes(data.path("toolCodes"), result);
            } else if ("tool".equals(kind(data))) {
                addCode(data.path("toolCode"), result);
            }
        }
        return List.copyOf(result);
    }

    private static boolean clearUnderstandToolConfig(ObjectNode data) {
        boolean changed = data.has("toolMode");
        data.remove("toolMode");
        return clearToolCodes(data) || changed;
    }

    private static boolean clearToolCodes(ObjectNode data) {
        JsonNode toolCodes = data.path("toolCodes");
        if (!toolCodes.isArray() || toolCodes.size() > 0) {
            data.set("toolCodes", OBJECT_MAPPER.createArrayNode());
            return true;
        }
        return false;
    }

    private static String kind(JsonNode data) {
        return data.path("kind").asText("").trim();
    }

    private static boolean isToolModeNone(JsonNode data) {
        return "NONE".equals(data.path("toolMode").asText("").trim().toUpperCase(Locale.ROOT));
    }

    private static boolean migrateLegacyActiveMode(ObjectNode data) {
        if (!"ACTIVE".equals(data.path("toolMode").asText("").trim().toUpperCase(Locale.ROOT))) {
            return false;
        }
        data.put("toolMode", "AUTO");
        data.put("toolConfigDeclared", true);
        return true;
    }

    private static boolean declaresNodeToolConfig(JsonNode data) {
        if (data.path("toolConfigDeclared").asBoolean(false)) {
            return true;
        }
        if (data.path("toolCodes").isArray() && data.path("toolCodes").size() > 0) {
            return true;
        }
        String mode = data.path("toolMode").asText("").trim().toUpperCase(Locale.ROOT);
        return Set.of("AUTO", "ACTIVE", "REQUIRED").contains(mode);
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

    public record NormalizedWorkflow(String workflowJson, List<String> toolCodes, boolean declaresTools) {
        public NormalizedWorkflow {
            toolCodes = toolCodes == null ? List.of() : List.copyOf(toolCodes);
        }
    }
}
