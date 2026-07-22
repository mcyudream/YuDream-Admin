package online.yudream.base.application.platform.agent.workflow.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNode;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeHandler;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeResult;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowRunState;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowValueResolver;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.enumerate.AiToolMode;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.AiAgentToolExecutionScope;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;
import online.yudream.base.domain.platform.ai.valobj.AiStructuredOutput;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Native chat-model handler. Each instance has one explicit workflow node semantic. */
public final class AgentLlmNodeHandler implements AgentWorkflowNodeHandler {
    private static final List<String> MODEL_KINDS = List.of("llm", "extract", "classify", "vision", "understand");
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() { };

    private final String kind;
    private final AgentWorkflowValueResolver values;
    private final ObjectMapper objectMapper;
    private final AiGenerationGateway gateway;
    private final AgentWorkflowRunState state;

    public AgentLlmNodeHandler(
            String kind,
            AgentWorkflowValueResolver values,
            ObjectMapper objectMapper,
            AiGenerationGateway gateway,
            AgentWorkflowRunState state
    ) {
        if (!MODEL_KINDS.contains(kind)) {
            throw new IllegalArgumentException("不支持的模型节点类型: " + kind);
        }
        this.kind = kind;
        this.values = values;
        this.objectMapper = objectMapper;
        this.gateway = gateway;
        this.state = state;
    }

    @Override
    public String kind() {
        return kind;
    }

    @Override
    public AgentWorkflowNodeResult execute(AgentWorkflowNode node, AgentWorkflowContext context) {
        boolean allowModelOverride = values.bool(node, "allowModelOverride", false);
        String providerCode = selected(values.text(node, "providerCode"), state.command().getProviderCode(), allowModelOverride);
        String modelCode = selected(values.text(node, "modelCode"), state.command().getModelCode(), allowModelOverride);
        if (!StringUtils.hasText(providerCode) || !StringUtils.hasText(modelCode)) {
            throw new BizException(node.title() + "必须选择可用模型");
        }

        AiToolMode toolMode = toolMode(node);
        List<AiAgentTool> tools = toolMode == AiToolMode.NONE
                ? List.of()
                : state.resolveModelTools(toolCodes(node));
        if (toolMode == AiToolMode.REQUIRED && tools.isEmpty()) {
            throw new BizException(node.title() + "必须配置至少一个可调用工具");
        }

        String nodePrompt = values.render(values.text(node, "prompt"), context);
        String userPrompt = stringify(values.input(node, context));
        String image = image(node, context);
        AiGenerationRequest request = new AiGenerationRequest(
                systemPrompt(nodePrompt, node, toolMode),
                userPrompt,
                image,
                providerCode,
                modelCode,
                state.aiConfig(),
                state.command().getHistory(),
                toolMode != AiToolMode.NONE && !tools.isEmpty(),
                toolMode,
                structuredOutput(node)
        );

        int toolResultsBefore = state.toolResultCount();
        List<online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult> callbackResults = new ArrayList<>();
        AiGenerationResult generated;
        try (AiAgentToolExecutionScope ignored = AiAgentToolExecutionScope.open(tools)) {
            generated = "understand".equals(kind)
                    ? gateway.generate(request)
                    : gateway.generateStream(request, state::emitDelta, result -> {
                        callbackResults.add(result);
                        state.addToolResult(result);
                    }, null);
        }
        addFinalToolResults(generated.toolResults(), callbackResults);
        if (toolMode == AiToolMode.REQUIRED && state.toolResultCount() == toolResultsBefore) {
            throw new BizException(node.title() + "必须调用工具后才能继续");
        }
        return values.result(node, output(node, generated.summary()));
    }

    private Object output(AgentWorkflowNode node, String summary) {
        return switch (kind) {
            case "extract" -> extracted(node, summary);
            case "classify" -> classified(node, summary);
            case "understand" -> structured(summary, values.bool(node, "strictJson", true), "问题理解节点未返回合法 JSON");
            default -> summary == null ? "" : summary;
        };
    }

    private Object extracted(AgentWorkflowNode node, String summary) {
        Object output = structured(summary, true, "信息提取节点未返回合法 JSON");
        Map<String, Object> schema = outputSchema(node);
        if (schema.isEmpty()) {
            return output;
        }
        if (!(output instanceof Map<?, ?> object)) {
            throw new BizException("信息提取节点必须返回 JSON 对象");
        }
        Object requiredValue = schema.get("required");
        if (requiredValue instanceof List<?> required) {
            for (Object field : required) {
                String name = field == null ? "" : field.toString().trim();
                if (name.isEmpty()) {
                    continue;
                }
                Object value = object.get(name);
                if (value == null || (value instanceof String text && text.isBlank())) {
                    throw new BizException("信息提取节点缺少必填字段: " + name);
                }
            }
        }
        return output;
    }

    private String classified(AgentWorkflowNode node, String summary) {
        List<String> classes = strings(node.data().path("classes"));
        if (classes.isEmpty()) {
            throw new BizException("分类节点必须配置分类项");
        }
        String answer = summary == null ? "" : summary.trim();
        if (answer.startsWith("\"") && answer.endsWith("\"")) {
            try {
                answer = objectMapper.readValue(answer, String.class).trim();
            } catch (Exception ignored) {
                // A plain class label remains the compatibility fallback.
            }
        }
        if (!classes.contains(answer)) {
            throw new BizException("分类节点返回了未配置的分类: " + answer);
        }
        return answer;
    }

    private AiToolMode toolMode(AgentWorkflowNode node) {
        if ("understand".equals(kind)) {
            return AiToolMode.NONE;
        }
        String configured = values.text(node, "toolMode");
        if (!configured.isBlank()) {
            try {
                return AiToolMode.valueOf(configured.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new BizException("模型节点工具调用策略无效: " + configured);
            }
        }
        return toolCodes(node).isEmpty() ? AiToolMode.NONE : AiToolMode.AUTO;
    }

    private List<String> toolCodes(AgentWorkflowNode node) {
        return strings(node.data().path("toolCodes"));
    }

    private List<String> strings(JsonNode source) {
        if (!source.isArray()) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        source.forEach(value -> {
            String text = value.asText("").trim();
            if (!text.isEmpty()) {
                result.add(text);
            }
        });
        return List.copyOf(result);
    }

    private Map<String, Object> outputSchema(AgentWorkflowNode node) {
        JsonNode raw = node.data().path("outputSchema");
        if (raw.isMissingNode() || raw.isNull() || raw.isEmpty()) {
            return Map.of();
        }
        try {
            if (raw.isObject()) {
                return objectMapper.convertValue(raw, JSON_OBJECT);
            }
            if (raw.isTextual() && !raw.asText().isBlank()) {
                return objectMapper.readValue(raw.asText(), JSON_OBJECT);
            }
        } catch (Exception exception) {
            throw new BizException("信息提取节点输出格式必须是 JSON 对象");
        }
        throw new BizException("信息提取节点输出格式必须是 JSON 对象");
    }

    private AiStructuredOutput structuredOutput(AgentWorkflowNode node) {
        if ("understand".equals(kind)) {
            return AiStructuredOutput.jsonObject();
        }
        if (!"extract".equals(kind)) {
            return AiStructuredOutput.none();
        }
        Map<String, Object> schema = outputSchema(node);
        return schema.isEmpty()
                ? AiStructuredOutput.jsonObject()
                : AiStructuredOutput.jsonSchema("agent_extract_result", schema, false);
    }

    private String image(AgentWorkflowNode node, AgentWorkflowContext context) {
        if (!"vision".equals(kind)) {
            return null;
        }
        String configuredVariable = values.text(node, "imageVariable");
        Object configured = configuredVariable.isBlank() ? null : values.resolve(configuredVariable, context);
        String image = imageData(configured);
        if (image == null) {
            image = state.command().getImageDataUrl();
        }
        if (!StringUtils.hasText(image)) {
            throw new BizException("视觉理解节点必须提供图片输入");
        }
        return image;
    }

    private String imageData(Object value) {
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of("imageDataUrl", "dataUrl", "url")) {
                Object candidate = map.get(key);
                if (candidate instanceof String text && StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        if (value instanceof Iterable<?> values) {
            for (Object item : values) {
                String image = imageData(item);
                if (image != null) {
                    return image;
                }
            }
        }
        return null;
    }

    private String systemPrompt(String nodePrompt, AgentWorkflowNode node, AiToolMode toolMode) {
        String applicationPrompt = state.application().getSystemPrompt() == null ? "" : state.application().getSystemPrompt().trim();
        String runtimePrompt = state.command().getRuntimeSystemPrompt() == null ? "" : state.command().getRuntimeSystemPrompt().trim();
        String role = "你正在作为 Agent 应用“" + state.application().getName() + "”工作。";
        String format = switch (kind) {
            case "extract", "understand" -> "仅输出合法 JSON，不要使用 Markdown 代码块。";
            case "classify" -> "只能输出一个分类值: " + String.join("、", strings(node.data().path("classes"))) + "。";
            case "vision" -> "基于输入图片回答用户问题。";
            default -> "直接给出对用户有用的结果。";
        };
        String toolInstruction = toolMode == AiToolMode.REQUIRED ? "必须至少调用一次可用工具后再回答。" : "";
        return String.join("\n", List.of(applicationPrompt, runtimePrompt, role, nodePrompt, format).stream()
                .filter(StringUtils::hasText)
                .toList());
    }

    private void addFinalToolResults(
            List<online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult> results,
            List<online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult> callbackResults
    ) {
        if (results == null || results.isEmpty()) {
            return;
        }
        List<online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult> pendingCallbacks = new ArrayList<>(callbackResults);
        for (var result : results) {
            int callbackIndex = matchingIndex(pendingCallbacks, result);
            if (callbackIndex >= 0) {
                pendingCallbacks.remove(callbackIndex);
                continue;
            }
            state.addToolResult(result);
        }
    }

    private int matchingIndex(List<?> values, Object candidate) {
        for (int index = 0; index < values.size(); index++) {
            if (Objects.equals(values.get(index), candidate)) {
                return index;
            }
        }
        return -1;
    }

    private Object structured(String summary, boolean strictJson, String invalidJsonMessage) {
        String json = summary == null ? "" : summary.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception exception) {
            if (!strictJson) {
                return Map.of("raw", json);
            }
            throw new BizException(invalidJsonMessage);
        }
    }

    private String stringify(Object input) {
        if (input instanceof String value) {
            return value;
        }
        try {
            return objectMapper.writeValueAsString(input);
        } catch (Exception exception) {
            throw new BizException("无法序列化模型节点输入");
        }
    }

    private String selected(String nodeValue, String requestValue, boolean allowOverride) {
        if (allowOverride && StringUtils.hasText(requestValue)) {
            return requestValue;
        }
        return StringUtils.hasText(nodeValue) ? nodeValue : requestValue;
    }
}
