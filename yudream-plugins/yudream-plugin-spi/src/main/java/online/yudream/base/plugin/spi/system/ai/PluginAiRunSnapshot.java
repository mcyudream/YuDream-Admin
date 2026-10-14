package online.yudream.base.plugin.spi.system.ai;

import java.util.List;

/** 后端任务的公开投影，不包含模型密钥、原生状态或执行权限快照。cursor 保持字符串。 */
public record PluginAiRunSnapshot(String runId, String sessionId, String status, String cursor,
                                  String content, String reasoning, List<PluginAiToolResult> toolResults,
                                  String error, boolean resumeSupported, PluginAiChatResponse response) {
    public PluginAiRunSnapshot {
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
    }
}
