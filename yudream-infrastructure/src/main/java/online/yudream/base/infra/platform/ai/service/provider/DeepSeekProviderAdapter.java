package online.yudream.base.infra.platform.ai.service.provider;

import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class DeepSeekProviderAdapter extends AbstractOpenAiCompatibleProviderAdapter {

    @Override
    public AiProviderType type() {
        return AiProviderType.DEEPSEEK;
    }

    @Override
    protected void applyProviderDefaults(
            AiProviderEndpoint provider,
            AiModelEndpoint model,
            AiGenerationRequest request,
            Map<String, Object> extraBody
    ) {
        if (request.toolCallingEnabled()) {
            extraBody.put("thinking", thinking("disabled"));
            return;
        }
        if (!extraBody.containsKey("thinking")) {
            extraBody.put("thinking", thinking(Boolean.TRUE.equals(model.thinkingEnabled()) ? "enabled" : "disabled"));
        }
    }

    @Override
    protected String reasoningEffort(
            AiProviderEndpoint provider,
            AiModelEndpoint model,
            AiGenerationRequest request,
            Map<String, Object> extraBody
    ) {
        return thinkingEnabled(extraBody.get("thinking")) ? firstText(model.reasoningEffort(), "") : "";
    }

    private boolean thinkingEnabled(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object type = map.get("type");
            return "enabled".equalsIgnoreCase(type == null ? "" : String.valueOf(type));
        }
        return false;
    }
}
