package online.yudream.base.plugin.spi.system.ai;

import java.util.concurrent.CompletionStage;

/** 持久运行凭据。result 在本阶段完成或等待用户时结束；跨进程恢复使用 runId 查询。 */
public record PluginAiRunHandle(String runId, CompletionStage<PluginAiChatResponse> result) { }
