package online.yudream.base.application.platform.agent.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public final class AgentWorkflowValidator {

    private static final Set<String> SUPPORTED_KINDS = Set.of(
            "start", "input", "end", "understand", "condition", "code", "template",
            "search", "vector", "rerank", "document", "citation", "llm", "embedding", "tool"
    );

    private final AgentWorkflowGraphParser parser;

    public AgentWorkflowValidator(ObjectMapper objectMapper) {
        this.parser = new AgentWorkflowGraphParser(objectMapper);
    }

    public void validate(String workflowJson, Catalog catalog) {
        AgentWorkflowGraph graph = parser.parse(workflowJson);
        Catalog available = catalog == null ? Catalog.empty() : catalog;
        List<AgentWorkflowNode> ends = graph.topologicalOrder().stream()
                .filter(node -> "end".equals(node.kind()))
                .toList();
        if (ends.size() != 1) {
            throw new AgentWorkflowDefinitionException("工作流必须且只能包含一个结束节点");
        }
        Set<String> reachable = reachableNodeIds(graph);
        if (!reachable.contains(ends.getFirst().id())) {
            throw new AgentWorkflowDefinitionException("结束节点必须能够从开始节点到达");
        }
        for (AgentWorkflowNode node : graph.topologicalOrder()) {
            validateNode(node, graph, available);
        }
    }

    private Set<String> reachableNodeIds(AgentWorkflowGraph graph) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(graph.startNode().id());
        while (!queue.isEmpty()) {
            String nodeId = queue.removeFirst();
            if (!visited.add(nodeId)) {
                continue;
            }
            graph.outgoingEdges(nodeId).forEach(edge -> queue.addLast(edge.target()));
        }
        return visited;
    }

    private void validateNode(AgentWorkflowNode node, AgentWorkflowGraph graph, Catalog catalog) {
        if (!SUPPORTED_KINDS.contains(node.kind())) {
            throw invalid(node, "节点类型不受支持：" + node.kind());
        }
        switch (node.kind()) {
            case "condition" -> validateCondition(node, graph);
            case "code" -> require(node, "code", "Python 代码");
            case "template" -> require(node, "template", "模板内容");
            case "search", "vector" -> validateKnowledgeNode(node, catalog);
            case "embedding", "rerank" -> validateModelNode(node, node.kind(), catalog);
            case "llm", "understand" -> validateModelNode(node, "chat", catalog);
            case "document" -> require(node, "documentInput", "文档输入");
            case "citation" -> require(node, "citationSource", "引用来源");
            case "tool" -> validateToolNode(node, catalog);
            default -> {
            }
        }
    }

    private void validateCondition(AgentWorkflowNode node, AgentWorkflowGraph graph) {
        require(node, "condition", "条件表达式");
        Set<String> handles = graph.outgoingEdges(node.id()).stream()
                .map(AgentWorkflowEdge::sourceHandle)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        if (!handles.contains("true") || !handles.contains("false")) {
            throw invalid(node, "条件节点必须同时连接 true 和 false 分支");
        }
    }

    private void validateKnowledgeNode(AgentWorkflowNode node, Catalog catalog) {
        String slug = require(node, "knowledgeSpaceSlug", "知识空间");
        if (!catalog.knowledgeSpaceSlugs().contains(slug)) {
            throw invalid(node, "知识空间不可用：" + slug);
        }
    }

    private void validateModelNode(AgentWorkflowNode node, String kind, Catalog catalog) {
        String providerCode = require(node, "providerCode", "模型提供方");
        String modelCode = require(node, "modelCode", "模型");
        if (!catalog.models().contains(new ModelRef(providerCode, modelCode, kind))) {
            throw invalid(node, "模型未配置或类型不匹配：" + providerCode + "/" + modelCode);
        }
    }

    private void validateToolNode(AgentWorkflowNode node, Catalog catalog) {
        String toolCode = require(node, "toolCode", "工具");
        if (!catalog.toolCodes().contains(toolCode)) {
            throw invalid(node, "工具不存在、未启用或当前用户无权使用：" + toolCode);
        }
    }

    private String require(AgentWorkflowNode node, String field, String label) {
        JsonNode value = node.data().get(field);
        String text = value == null || value.isNull() ? "" : value.asText().trim();
        if (!StringUtils.hasText(text)) {
            throw invalid(node, label + "不能为空");
        }
        return text;
    }

    private AgentWorkflowDefinitionException invalid(AgentWorkflowNode node, String message) {
        return new AgentWorkflowDefinitionException("节点“" + node.title() + "”：" + message);
    }

    public record ModelRef(String providerCode, String modelCode, String kind) {
    }

    public record Catalog(Set<ModelRef> models, Set<String> knowledgeSpaceSlugs, Set<String> toolCodes) {
        public Catalog {
            models = models == null ? Set.of() : Set.copyOf(models);
            knowledgeSpaceSlugs = knowledgeSpaceSlugs == null ? Set.of() : Set.copyOf(knowledgeSpaceSlugs);
            toolCodes = toolCodes == null ? Set.of() : Set.copyOf(toolCodes);
        }

        public static Catalog empty() {
            return new Catalog(Set.of(), Set.of(), Set.of());
        }
    }
}
