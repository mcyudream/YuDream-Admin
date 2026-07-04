package online.yudream.base.domain.platform.ai.valobj;

import java.util.Map;

public record AiGenerationRequest(
        String systemPrompt,
        String userPrompt,
        String imageDataUrl,
        String model,
        Map<String, String> config
) {
}
