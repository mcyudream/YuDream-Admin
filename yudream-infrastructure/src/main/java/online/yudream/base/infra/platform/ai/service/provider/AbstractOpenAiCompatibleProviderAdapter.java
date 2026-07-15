package online.yudream.base.infra.platform.ai.service.provider;

import cn.hutool.json.JSONUtil;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractOpenAiCompatibleProviderAdapter implements AiProviderAdapter {

    private static final String DEFAULT_TEMPERATURE = "0.4";

    @Override
    public OpenAiChatOptions chatOptions(AiProviderEndpoint provider, AiModelEndpoint model, AiGenerationRequest request) {
        Map<String, Object> extraBody = extraBody(provider, model, request);
        applyProviderDefaults(provider, model, request, extraBody);
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(model.modelName())
                .temperature(temperature(provider, model))
                .toolChoice(request.providerToolChoice());
        String reasoningEffort = reasoningEffort(provider, model, request, extraBody);
        if (StringUtils.hasText(reasoningEffort)) {
            builder.reasoningEffort(reasoningEffort);
        }
        if (!extraBody.isEmpty()) {
            builder.extraBody(extraBody);
        }
        return builder.build();
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
                .replace("{{embeddingModel}}", firstText(provider.embeddingModels().stream().findFirst().orElse(""), ""))
                .replace("{{rerankModel}}", firstText(provider.rerankModels().stream().findFirst().orElse(""), ""))
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
