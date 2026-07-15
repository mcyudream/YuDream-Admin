package online.yudream.base.domain.platform.ai.valobj;

import online.yudream.base.domain.platform.ai.enumerate.AiToolMode;

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
        AiToolMode toolMode
) {
    public AiGenerationRequest {
        toolMode = toolMode == null
                ? (toolCallingEnabled ? AiToolMode.AUTO : AiToolMode.NONE)
                : toolMode;
    }

    public AiGenerationRequest(
            String systemPrompt,
            String userPrompt,
            String imageDataUrl,
            String providerCode,
            String modelCode,
            Map<String, String> config
    ) {
        this(systemPrompt, userPrompt, imageDataUrl, providerCode, modelCode, config, List.of(), false, AiToolMode.NONE);
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
        this(systemPrompt, userPrompt, imageDataUrl, providerCode, modelCode, config, history, false, AiToolMode.NONE);
    }

    public AiGenerationRequest(
            String systemPrompt,
            String userPrompt,
            String imageDataUrl,
            String providerCode,
            String modelCode,
            Map<String, String> config,
            List<AiChatMessage> history,
            boolean toolCallingEnabled
    ) {
        this(
                systemPrompt,
                userPrompt,
                imageDataUrl,
                providerCode,
                modelCode,
                config,
                history,
                toolCallingEnabled,
                toolCallingEnabled ? AiToolMode.AUTO : AiToolMode.NONE
        );
    }

    public List<AiChatMessage> history() {
        return history == null ? List.of() : history;
    }

    public AiGenerationRequest withToolCallingEnabled(boolean enabled) {
        return new AiGenerationRequest(
                systemPrompt,
                userPrompt,
                imageDataUrl,
                providerCode,
                modelCode,
                config,
                history(),
                enabled,
                toolMode
        );
    }

}
