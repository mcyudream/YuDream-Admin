package online.yudream.base.plugin.spi.system.ai;

import java.util.List;

public record PluginAiChatResponse(String content, List<PluginAiToolResult> toolResults) {
    public PluginAiChatResponse {
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
    }
}
