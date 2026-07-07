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
        List<AiChatMessage> history
) {
    public AiGenerationRequest(
            String systemPrompt,
            String userPrompt,
            String imageDataUrl,
            String providerCode,
            String modelCode,
            Map<String, String> config
    ) {
        this(systemPrompt, userPrompt, imageDataUrl, providerCode, modelCode, config, List.of());
    }

    public List<AiChatMessage> history() {
        return history == null ? List.of() : history;
    }
}
