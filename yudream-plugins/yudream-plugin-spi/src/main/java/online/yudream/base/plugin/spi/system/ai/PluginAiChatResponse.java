package online.yudream.base.plugin.spi.system.ai;

import java.util.List;

public record PluginAiChatResponse(String content, List<PluginAiToolResult> toolResults, String runId, String status) {
    public PluginAiChatResponse {
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
        status = status == null ? "COMPLETED" : status;
    }

    public PluginAiChatResponse(String content, List<PluginAiToolResult> toolResults) {
        this(content, toolResults, null, "COMPLETED");
    }
}
