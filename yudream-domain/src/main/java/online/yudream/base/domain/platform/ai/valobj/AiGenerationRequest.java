package online.yudream.base.domain.platform.ai.valobj;

import java.util.Map;

public record AiGenerationRequest(
        String systemPrompt,
        String userPrompt,
        Map<String, String> config
) {
}
