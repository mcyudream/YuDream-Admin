package online.yudream.base.plugin.spi.system.ai;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public interface PluginAiService {
    CompletionStage<PluginAiChatResponse> chat(PluginAiChatRequest request);
    java.util.List<PluginAiToolDescriptor> tools();
    java.util.List<PluginAiProviderOption> providers();
    java.util.List<PluginAiAgentOption> agents();
    CompletionStage<PluginAiChatResponse> runAgent(String agentCode, PluginAiChatRequest request);

    /**
     * 流式对话：onDelta 收到模型输出增量（含 reasoning 时也可能推送思考片段）。
     * 默认实现退化为一次性 chat 后整段回放，宿主可覆盖为真流式。
     */
    default CompletionStage<PluginAiChatResponse> chatStream(PluginAiChatRequest request, Consumer<String> onDelta) {
        return chatStream(request, onDelta, null);
    }

    /**
     * 流式对话 + 工具调用回调：每次工具执行完成回调 onTool（逐题入库等场景可实时感知进度）。
     * 插件写工具需在 executionContext.grantedWriteToolNames 中逐个点名授权，否则宿主只放行 READ 工具。
     */
    default CompletionStage<PluginAiChatResponse> chatStream(
            PluginAiChatRequest request,
            Consumer<String> onDelta,
            Consumer<PluginAiToolResult> onTool
    ) {
        return chat(request).thenApply(response -> {
            if (onDelta != null && response.content() != null && !response.content().isEmpty()) {
                onDelta.accept(response.content());
            }
            if (onTool != null && response.toolResults() != null) {
                response.toolResults().forEach(onTool);
            }
            return response;
        });
    }

    default CompletionStage<PluginAiChatResponse> runAgentStream(
            String agentCode,
            PluginAiChatRequest request,
            Consumer<String> onDelta
    ) {
        return runAgent(agentCode, request).thenApply(response -> {
            if (onDelta != null && response.content() != null && !response.content().isEmpty()) {
                onDelta.accept(response.content());
            }
            return response;
        });
    }
}
