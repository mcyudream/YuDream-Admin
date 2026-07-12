package online.yudream.base.plugin.spi.system.ai;

import java.util.List;

public record PluginAiChatRequest(
        String systemPrompt,
        String userPrompt,
        String providerCode,
        String modelCode,
        List<PluginAiChatMessage> history,
        PluginAiExecutionContext executionContext,
        boolean toolCallingEnabled
) {
    public PluginAiChatRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
