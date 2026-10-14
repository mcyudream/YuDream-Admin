package online.yudream.base.plugin.spi.system.ai;

import java.util.List;

public record PluginAiChatRequest(
        String systemPrompt,
        String userPrompt,
        String providerCode,
        String modelCode,
        List<PluginAiChatMessage> history,
        PluginAiExecutionContext executionContext,
        boolean toolCallingEnabled,
        String sessionId
) {
    public PluginAiChatRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }

    public PluginAiChatRequest(String systemPrompt, String userPrompt, String providerCode, String modelCode,
                               List<PluginAiChatMessage> history, PluginAiExecutionContext executionContext,
                               boolean toolCallingEnabled) {
        this(systemPrompt, userPrompt, providerCode, modelCode, history, executionContext, toolCallingEnabled, null);
    }
}
