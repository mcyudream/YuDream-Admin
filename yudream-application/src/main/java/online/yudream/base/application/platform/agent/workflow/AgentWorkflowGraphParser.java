package online.yudream.base.application.platform.agent.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AgentWorkflowGraphParser {

    private final ObjectMapper objectMapper;

    public AgentWorkflowGraphParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AgentWorkflowGraph parse(String workflowJson) {
        if (workflowJson == null || workflowJson.isBlank()) {
            throw new AgentWorkflowDefinitionException("工作流 JSON 不能为空");
        }
        try {
            return parseRoot(objectMapper.readTree(workflowJson));
        } catch (AgentWorkflowDefinitionException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AgentWorkflowDefinitionException("工作流 JSON 格式无效", exception);
        }
    }

    private AgentWorkflowGraph parseRoot(JsonNode root) {
        if (!root.isObject() || !root.path("nodes").isArray() || !root.path("edges").isArray()) {
            throw new AgentWorkflowDefinitionException("工作流必须包含 nodes 和 edges 数组");
        }
        Map<String, AgentWorkflowNode> nodes = parseNodes(root.path("nodes"));
        AgentWorkflowNode startNode = uniqueStartNode(nodes);
        Map<String, List<AgentWorkflowEdge>> outgoing = new LinkedHashMap<>();
        Map<String, Integer> indegree = new LinkedHashMap<>();
        nodes.keySet().forEach(id -> indegree.put(id, 0));
        parseEdges(root.path("edges"), nodes, outgoing, indegree);
        List<AgentWorkflowNode> order = topologicalOrder(nodes, outgoing, indegree);
        return new AgentWorkflowGraph(nodes, outgoing, order, startNode);
    }

    private Map<String, AgentWorkflowNode> parseNodes(JsonNode nodeItems) {
        Map<String, AgentWorkflowNode> nodes = new LinkedHashMap<>();
        for (JsonNode item : nodeItems) {
            String id = requiredText(item, "id", "节点 id 不能为空");
            JsonNode data = item.path("data");
            if (!data.isObject()) {
                throw new AgentWorkflowDefinitionException("节点 " + id + " 缺少 data 对象");
            }
            String kind = requiredText(data, "kind", "节点 " + id + " 的 kind 不能为空");
            String title = textOrNull(data, "title");
            if (title == null) {
                title = textOrNull(data, "label");
            }
            AgentWorkflowNode previous = nodes.putIfAbsent(
                    id,
                    new AgentWorkflowNode(id, kind, title == null ? id : title, data.deepCopy())
            );
            if (previous != null) {
                throw new AgentWorkflowDefinitionException("工作流节点 id 重复：" + id);
            }
        }
        if (nodes.isEmpty()) {
            throw new AgentWorkflowDefinitionException("工作流至少需要一个节点");
        }
        return nodes;
    }

    private void parseEdges(
            JsonNode edgeItems,
            Map<String, AgentWorkflowNode> nodes,
            Map<String, List<AgentWorkflowEdge>> outgoing,
            Map<String, Integer> indegree
    ) {
        int index = 0;
        for (JsonNode item : edgeItems) {
            String source = requiredText(item, "source", "边的 source 不能为空");
            String target = requiredText(item, "target", "边的 target 不能为空");
            if (!nodes.containsKey(source) || !nodes.containsKey(target)) {
                throw new AgentWorkflowDefinitionException("边引用了不存在的节点：" + source + " -> " + target);
            }
            String id = textOrNull(item, "id");
            String sourceHandle = textOrNull(item, "sourceHandle");
            if ("condition".equals(nodes.get(source).kind())
                    && (!StringUtils.hasText(sourceHandle) || "source".equals(sourceHandle))) {
                String legacyBranch = firstText(textOrNull(item, "label"), textOrNull(item.path("data"), "branch"));
                if ("true".equalsIgnoreCase(legacyBranch) || "false".equalsIgnoreCase(legacyBranch)) {
                    sourceHandle = legacyBranch.toLowerCase();
                }
            }
            AgentWorkflowEdge edge = new AgentWorkflowEdge(
                    id == null ? "edge-" + index : id,
                    source,
                    target,
                    sourceHandle,
                    textOrNull(item, "targetHandle")
            );
            outgoing.computeIfAbsent(source, ignored -> new ArrayList<>()).add(edge);
            indegree.compute(target, (ignored, value) -> value == null ? 1 : value + 1);
            index++;
        }
    }

    private AgentWorkflowNode uniqueStartNode(Map<String, AgentWorkflowNode> nodes) {
        List<AgentWorkflowNode> starts = nodes.values().stream()
                .filter(node -> "start".equals(node.kind()))
                .toList();
        if (starts.size() != 1) {
            throw new AgentWorkflowDefinitionException("工作流必须且只能包含一个开始节点");
        }
        return starts.getFirst();
    }

    private List<AgentWorkflowNode> topologicalOrder(
            Map<String, AgentWorkflowNode> nodes,
            Map<String, List<AgentWorkflowEdge>> outgoing,
            Map<String, Integer> indegree
    ) {
        ArrayDeque<String> queue = new ArrayDeque<>();
        indegree.forEach((id, count) -> {
            if (count == 0) {
                queue.addLast(id);
            }
        });
        List<AgentWorkflowNode> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            order.add(nodes.get(id));
            for (AgentWorkflowEdge edge : outgoing.getOrDefault(id, List.of())) {
                int remaining = indegree.compute(edge.target(), (ignored, value) -> value - 1);
                if (remaining == 0) {
                    queue.addLast(edge.target());
                }
            }
        }
        if (order.size() != nodes.size()) {
            throw new AgentWorkflowDefinitionException("工作流不能包含环");
        }
        return order;
    }

    private String requiredText(JsonNode node, String field, String message) {
        String value = textOrNull(node, field);
        if (value == null) {
            throw new AgentWorkflowDefinitionException(message);
        }
        return value;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
