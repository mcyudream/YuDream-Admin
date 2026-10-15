package online.yudream.base.infra.platform.ai.service.provider;

import cn.hutool.json.JSONUtil;
import io.agentscope.core.formatter.JsonSchema;
import io.agentscope.core.formatter.ResponseFormat;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolChoice;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.enumerate.AiToolMode;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiStructuredOutput;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把平台供应商配置（温度、extraBody 模板、thinking、reasoningEffort、结构化输出）
 * 装配为 AgentScope {@link GenerateOptions}；Kimi/DeepSeek 等差异在子类覆写。
 */
public abstract class AbstractOpenAiCompatibleProviderAdapter implements AiProviderAdapter {

    private static final String DEFAULT_TEMPERATURE = "0.4";

    @Override
    public GenerateOptions generateOptions(AiProviderEndpoint provider, AiModelEndpoint model, AiGenerationRequest request) {
        Map<String, Object> extraBody = extraBody(provider, model, request);
        applyProviderDefaults(provider, model, request, extraBody);
        GenerateOptions.Builder builder = GenerateOptions.builder()
                .temperature(temperature(provider, model));
        if (request.toolCallingEnabled()
                && request.toolMode() == AiToolMode.REQUIRED
                && supportsRequiredToolChoice(provider, model)) {
            builder.toolChoice(new ToolChoice.Required());
        }
        applyStructuredOutput(builder, request);
        String reasoningEffort = reasoningEffort(provider, model, request, extraBody);
        if (StringUtils.hasText(reasoningEffort)) {
            builder.reasoningEffort(reasoningEffort);
        }
        if (!extraBody.isEmpty()) {
            builder.additionalBodyParams(extraBody);
        }
        return builder.build();
    }

    private void applyStructuredOutput(GenerateOptions.Builder builder, AiGenerationRequest request) {
        AiStructuredOutput structured = request.structuredOutput();
        if (structured == null || !structured.enabled()) {
            return;
        }
        // JSON_SCHEMA 的原生/降级切换由框架结构化输出路径处理，这里只下发 JSON_OBJECT。
        if (structured.mode() == AiStructuredOutput.Mode.JSON_OBJECT) {
            builder.responseFormat(ResponseFormat.jsonObject());
        }
    }

    static JsonSchema jsonSchema(AiStructuredOutput structured) {
        JsonSchema.Builder builder = JsonSchema.builder()
                .name(StringUtils.hasText(structured.name()) ? structured.name() : "structured_result")
                .schema(structured.schema())
                .strict(structured.strict());
        return builder.build();
    }

    /** Compatibility providers opt in only when their native API guarantees `tool_choice=required`. */
    @Override
    public boolean supportsRequiredToolChoice(AiProviderEndpoint provider, AiModelEndpoint model) {
        return false;
    }

    protected void applyProviderDefaults(
            AiProviderEndpoint provider,
            AiModelEndpoint model,
            AiGenerationRequest request,
            Map<String, Object> extraBody
    ) {
    }

    protected Map<String, Object> extraBody(AiProviderEndpoint provider, AiModelEndpoint model, AiGenerationRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.putAll(parseExtraBody(provider.extraBody(), provider, model, request));
        result.putAll(parseExtraBody(model.extraBody(), provider, model, request));
        return result;
    }

    protected String reasoningEffort(
            AiProviderEndpoint provider,
            AiModelEndpoint model,
            AiGenerationRequest request,
            Map<String, Object> extraBody
    ) {
        return firstText(model.reasoningEffort(), "");
    }

    protected Map<String, Object> thinking(String type) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", type);
        return value;
    }

    protected Map<String, Object> thinking(String type, String keep) {
        Map<String, Object> value = thinking(type);
        value.put("keep", keep);
        return value;
    }

    private Map<String, Object> parseExtraBody(
            String extraBody,
            AiProviderEndpoint provider,
            AiModelEndpoint model,
            AiGenerationRequest request
    ) {
        String rendered = renderTemplate(extraBody, provider, model, request);
        if (!StringUtils.hasText(rendered)) {
            return Map.of();
        }
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            JSONUtil.parseObj(rendered).forEach(result::put);
            return result;
        } catch (Exception e) {
            throw new BizException("AI extraBody 不是有效 JSON：" + e.getMessage());
        }
    }

    private String renderTemplate(
            String value,
            AiProviderEndpoint provider,
            AiModelEndpoint model,
            AiGenerationRequest request
    ) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value
                .replace("{{provider}}", provider.code())
                .replace("{{providerCode}}", provider.code())
                .replace("{{model}}", model.modelName())
                .replace("{{modelCode}}", model.optionCode())
                .replace("{{thinkingEnabled}}", String.valueOf(Boolean.TRUE.equals(model.thinkingEnabled())))
                .replace("{{embeddingModel}}", firstText(provider.embeddingModelNames().stream().findFirst().orElse(""), ""))
                .replace("{{rerankModel}}", firstText(provider.rerankModelNames().stream().findFirst().orElse(""), ""))
                .replace("{{requestProviderCode}}", request.providerCode() == null ? "" : request.providerCode())
                .replace("{{requestModelCode}}", request.modelCode() == null ? "" : request.modelCode());
    }

    private double temperature(AiProviderEndpoint provider, AiModelEndpoint model) {
        String value = firstText(model.temperature(), provider.temperature(), DEFAULT_TEMPERATURE);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return Double.parseDouble(DEFAULT_TEMPERATURE);
        }
    }

    protected String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
