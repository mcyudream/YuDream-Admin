package online.yudream.base.application.platform.agent.workflow.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNode;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeHandler;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeResult;
import online.yudream.base.application.platform.agent.workflow.support.AgentKnowledgeOperations;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowValueResolver;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.valobj.WikiSearchHit;

import java.util.Collection;
import java.util.List;

public final class AgentKnowledgeNodeHandler implements AgentWorkflowNodeHandler {
    private static final TypeReference<List<WikiSearchHit>> HIT_LIST = new TypeReference<>() {};

    private final String kind;
    private final AgentWorkflowValueResolver values;
    private final ObjectMapper objectMapper;
    private final AgentKnowledgeOperations operations;

    public AgentKnowledgeNodeHandler(
            String kind,
            AgentWorkflowValueResolver values,
            ObjectMapper objectMapper,
            AgentKnowledgeOperations operations
    ) {
        if (!List.of("search", "vector", "rerank", "embedding").contains(kind)) {
            throw new IllegalArgumentException("不支持的知识节点类型：" + kind);
        }
        this.kind = kind;
        this.values = values;
        this.objectMapper = objectMapper;
        this.operations = operations;
    }

    @Override
    public String kind() {
        return kind;
    }

    @Override
    public AgentWorkflowNodeResult execute(AgentWorkflowNode node, AgentWorkflowContext context) {
        return switch (kind) {
            case "search" -> values.result(node, search(node, context, false));
            case "vector" -> values.result(node, search(node, context, true));
            case "rerank" -> values.result(node, rerank(node, context));
            case "embedding" -> values.result(node, embedding(node, context));
            default -> throw new BizException("不支持的知识节点类型：" + kind);
        };
    }

    private List<WikiSearchHit> search(AgentWorkflowNode node, AgentWorkflowContext context, boolean vectorOnly) {
        String space = required(values.text(node, "knowledgeSpaceSlug"), "知识节点必须选择知识空间");
        String query = text(values.input(node, context));
        int topK = Math.clamp(values.integer(node, "topK", 5), 1, 100);
        String path = values.text(node, "pathPrefix");
        boolean graph = values.bool(node, "graphExpansion", false);
        return vectorOnly
                ? operations.vectorSearch(space, query, topK, path, graph)
                : operations.search(space, query, topK, path, graph);
    }

    private List<WikiSearchHit> rerank(AgentWorkflowNode node, AgentWorkflowContext context) {
        List<WikiSearchHit> candidates;
        try {
            candidates = objectMapper.convertValue(values.input(node, context), HIT_LIST);
        } catch (IllegalArgumentException exception) {
            throw new BizException("重排节点输入必须是检索结果列表");
        }
        if (candidates == null || candidates.isEmpty()) {
            throw new BizException("重排节点没有可用的候选结果");
        }
        Object queryValue = context.variable("query");
        String query = queryValue instanceof String value ? value : "";
        return operations.rerank(
                values.text(node, "knowledgeSpaceSlug"),
                required(values.text(node, "providerCode"), "重排节点必须选择 Provider"),
                required(values.text(node, "modelCode"), "重排节点必须选择模型"),
                query,
                candidates
        );
    }

    private Object embedding(AgentWorkflowNode node, AgentWorkflowContext context) {
        Object input = values.input(node, context);
        List<String> texts = input instanceof Collection<?> collection
                ? collection.stream().map(this::text).toList()
                : List.of(text(input));
        List<List<Float>> vectors = operations.embedding(
                values.text(node, "knowledgeSpaceSlug"),
                values.text(node, "providerCode"),
                values.text(node, "modelCode"),
                texts
        );
        return texts.size() == 1 && !vectors.isEmpty() ? vectors.getFirst() : vectors;
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(message);
        }
        return value;
    }
}
