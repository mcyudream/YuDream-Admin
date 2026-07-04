package online.yudream.base.infra.platform.ai.service.provider;

import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class KimiProviderAdapter extends AbstractOpenAiCompatibleProviderAdapter {

    @Override
    public AiProviderType type() {
        return AiProviderType.KIMI;
    }

    @Override
    protected void applyProviderDefaults(
            AiProviderEndpoint provider,
            AiModelEndpoint model,
            AiGenerationRequest request,
            Map<String, Object> extraBody
    ) {
        if (extraBody.containsKey("thinking")) {
            return;
        }
        String modelName = model.modelName().toLowerCase();
        if (modelName.contains("k2.7-code")) {
            extraBody.put("thinking", thinking("enabled", "all"));
            return;
        }
        if (model.thinkingEnabled() == null) {
            return;
        }
        extraBody.put("thinking", thinking(Boolean.TRUE.equals(model.thinkingEnabled()) ? "enabled" : "disabled"));
    }
}
