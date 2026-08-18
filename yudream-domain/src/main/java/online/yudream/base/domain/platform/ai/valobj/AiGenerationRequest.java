package online.yudream.base.domain.platform.ai.valobj;

import online.yudream.base.domain.platform.ai.enumerate.AiToolMode;

import java.util.List;
import java.util.Map;

public record AiGenerationRequest(
        String systemPrompt,
        String userPrompt,
        String imageDataUrl,
        List<String> imageDataUrls,
        String providerCode,
        String modelCode,
        Map<String, String> config,
        List<AiChatMessage> history,
        boolean toolCallingEnabled,
        AiToolMode toolMode,
        AiStructuredOutput structuredOutput
) {
    public AiGenerationRequest {
        if (imageDataUrls == null || imageDataUrls.isEmpty()) {
            imageDataUrls = hasText(imageDataUrl) ? List.of(imageDataUrl) : List.of();
        }
        else if (!hasText(imageDataUrl)) {
            imageDataUrl = imageDataUrls.getFirst();
        }
        toolMode = toolMode == null
                ? (toolCallingEnabled ? AiToolMode.AUTO : AiToolMode.NONE)
                : toolMode;
        structuredOutput = structuredOutput == null ? AiStructuredOutput.none() : structuredOutput;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public AiGenerationRequest(
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
        this(systemPrompt, userPrompt, imageDataUrl, null, providerCode, modelCode, config, history,
                toolCallingEnabled, toolMode, AiStructuredOutput.none());
    }

    public AiGenerationRequest(
            String systemPrompt,
            String userPrompt,
            List<String> imageDataUrls,
            String providerCode,
            String modelCode,
            Map<String, String> config,
            List<AiChatMessage> history,
            boolean toolCallingEnabled,
            AiToolMode toolMode
    ) {
        this(systemPrompt, userPrompt, null, imageDataUrls, providerCode, modelCode, config, history,
                toolCallingEnabled, toolMode, AiStructuredOutput.none());
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
                imageDataUrls,
                providerCode,
                modelCode,
                config,
                history(),
                enabled,
                toolMode,
                structuredOutput
        );
    }

    public AiGenerationRequest withStructuredOutput(AiStructuredOutput output) {
        return new AiGenerationRequest(
                systemPrompt,
                userPrompt,
                imageDataUrl,
                imageDataUrls,
                providerCode,
                modelCode,
                config,
                history(),
                toolCallingEnabled,
                toolMode,
                output
        );
    }

}
