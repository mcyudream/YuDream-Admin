package online.yudream.base.application.platform.agent.workflow.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNode;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeHandler;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeResult;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowValueResolver;
import online.yudream.base.domain.common.exception.BizException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class AgentCitationNodeHandler implements AgentWorkflowNodeHandler {
    private static final TypeReference<List<Map<String, Object>>> CITATION_LIST = new TypeReference<>() {};

    private final AgentWorkflowValueResolver values;
    private final ObjectMapper objectMapper;

    @Override
    public String kind() {
        return "citation";
    }

    @Override
    public AgentWorkflowNodeResult execute(AgentWorkflowNode node, AgentWorkflowContext context) {
        String sourceVariable = values.text(node, "citationSource");
        Object source = sourceVariable.isBlank() ? values.input(node, context) : values.resolve(sourceVariable, context);
        List<Map<String, Object>> citations;
        try {
            citations = objectMapper.convertValue(source, CITATION_LIST);
        } catch (IllegalArgumentException exception) {
            throw new BizException("引用节点输入必须是检索结果列表");
        }
        if (citations == null || citations.isEmpty()) {
            throw new BizException("引用节点没有可用的检索结果");
        }
        List<Map<String, Object>> normalized = normalize(citations);
        Object output = "json".equalsIgnoreCase(values.text(node, "citationFormat"))
                ? normalized
                : markdown(normalized);
        return values.result(node, output);
    }

    private List<Map<String, Object>> normalize(List<Map<String, Object>> citations) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < citations.size(); i++) {
            Map<String, Object> item = citations.get(i);
            Map<String, Object> citation = new LinkedHashMap<>();
            citation.put("index", i + 1);
            citation.put("title", text(item.get("title"), "来源 " + (i + 1)));
            citation.put("path", text(item.get("path"), ""));
            citation.put("content", text(item.get("content"), ""));
            citation.put("sourceUrl", text(item.get("sourceUrl"), text(item.get("path"), "")));
            citation.put("score", item.getOrDefault("score", 0));
            result.add(Map.copyOf(citation));
        }
        return List.copyOf(result);
    }

    private String markdown(List<Map<String, Object>> citations) {
        StringBuilder result = new StringBuilder();
        for (Map<String, Object> citation : citations) {
            if (!result.isEmpty()) {
                result.append("\n");
            }
            result.append('[').append(citation.get("index")).append("] ")
                    .append(citation.get("title"));
            String sourceUrl = citation.get("sourceUrl").toString();
            if (!sourceUrl.isBlank()) {
                result.append(" (").append(sourceUrl).append(')');
            }
            String content = citation.get("content").toString();
            if (!content.isBlank()) {
                result.append(" - ").append(content);
            }
        }
        return result.toString();
    }

    private String text(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }
}
