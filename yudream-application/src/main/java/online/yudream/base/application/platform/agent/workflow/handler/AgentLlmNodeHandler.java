package online.yudream.base.application.platform.agent.workflow.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNode;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeHandler;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeResult;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowRunState;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowValueResolver;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiAgentToolExecutionScope;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;
import org.springframework.util.StringUtils;

import java.util.List;

public final class AgentLlmNodeHandler implements AgentWorkflowNodeHandler {
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
        if (!List.of("llm", "understand").contains(kind)) {
            throw new IllegalArgumentException("不支持的模型节点类型：" + kind);
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
        String providerCode = selected(
                values.text(node, "providerCode"), state.command().getProviderCode(), allowModelOverride
        );
        String modelCode = selected(values.text(node, "modelCode"), state.command().getModelCode(), allowModelOverride);
        if (!StringUtils.hasText(providerCode) || !StringUtils.hasText(modelCode)) {
            throw new BizException(node.title() + "必须选择可用模型");
        }
        String nodePrompt = values.render(values.text(node, "prompt"), context);
        String systemPrompt = systemPrompt(nodePrompt);
        String userPrompt = stringify(values.input(node, context));
        String image = "llm".equals(kind) && values.bool(node, "vision", false)
                ? state.command().getImageDataUrl()
                : null;
        AiGenerationRequest request = new AiGenerationRequest(
                systemPrompt,
                userPrompt,
                image,
                providerCode,
                modelCode,
                state.aiConfig(),
                state.command().getHistory()
        ).withToolCallingEnabled("llm".equals(kind)
                && (!state.systemToolCodes().isEmpty() || state.command().isRuntimeToolCallingEnabled()));
        AiGenerationResult generated;
        try (AiAgentToolExecutionScope ignored = AiAgentToolExecutionScope.open(state.systemToolCodes())) {
            generated = "understand".equals(kind)
                    ? gateway.generate(request)
                    : gateway.generateStream(request, state::emitDelta, state::addToolResult, null);
        }
        if (generated.toolResults() != null) {
            generated.toolResults().forEach(state::addToolResult);
        }
        Object output = "understand".equals(kind)
                ? structured(generated.summary(), values.bool(node, "strictJson", true))
                : generated.summary();
        return values.result(node, output == null ? "" : output);
    }

    private String systemPrompt(String nodePrompt) {
        String applicationPrompt = state.application().getSystemPrompt() == null
                ? ""
                : state.application().getSystemPrompt().trim();
        String runtimePrompt = state.command().getRuntimeSystemPrompt() == null
                ? ""
                : state.command().getRuntimeSystemPrompt().trim();
        String role = "你正在作为 Agent 应用“" + state.application().getName() + "”工作。";
        String format = "understand".equals(kind)
                ? "仅输出合法 JSON，不要使用 Markdown 代码块。"
                : "直接给出对用户有用的结果。";
        return String.join("\n", List.of(applicationPrompt, runtimePrompt, role, nodePrompt, format).stream()
                .filter(StringUtils::hasText)
                .toList());
    }

    private Object structured(String summary, boolean strictJson) {
        String json = summary == null ? "" : summary.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception exception) {
            if (!strictJson) {
                return java.util.Map.of("raw", json);
            }
            throw new BizException("问题理解节点未返回合法 JSON");
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
