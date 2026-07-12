package online.yudream.base.domain.platform.ai.valobj;

import java.util.List;
import java.util.Map;

public record AiGenerationRequest(
        String systemPrompt,
        String userPrompt,
        String imageDataUrl,
        String providerCode,
        String modelCode,
        Map<String, String> config,
        List<AiChatMessage> history,
        boolean toolCallingEnabled,
        boolean structuredOutputRequired
) {
    public AiGenerationRequest(
            String systemPrompt,
            String userPrompt,
            String imageDataUrl,
            String providerCode,
            String modelCode,
            Map<String, String> config
    ) {
        this(systemPrompt, userPrompt, imageDataUrl, providerCode, modelCode, config, List.of(), false, false);
    }

    public AiGenerationRequest(
            String systemPrompt,
            String userPrompt,
            String imageDataUrl,
            String providerCode,
            String modelCode,
            Map<String, String> config,
            List<AiChatMessage> history
    ) {
        this(systemPrompt, userPrompt, imageDataUrl, providerCode, modelCode, config, history, false, false);
    }

    public List<AiChatMessage> history() {
        return history == null ? List.of() : history;
    }

    public AiGenerationRequest withToolCallingEnabled(boolean enabled) {
        return new AiGenerationRequest(systemPrompt, userPrompt, imageDataUrl, providerCode, modelCode, config, history(), enabled, structuredOutputRequired);
    }

    public AiGenerationRequest withStructuredOutputRequired(boolean required) {
        return new AiGenerationRequest(systemPrompt, userPrompt, imageDataUrl, providerCode, modelCode, config, history(), toolCallingEnabled, required);
    }
}
