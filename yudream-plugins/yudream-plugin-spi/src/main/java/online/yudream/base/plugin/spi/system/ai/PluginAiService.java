package online.yudream.base.plugin.spi.system.ai;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public interface PluginAiService {
    CompletionStage<PluginAiChatResponse> chat(PluginAiChatRequest request);
    java.util.List<PluginAiToolDescriptor> tools();
    java.util.List<PluginAiProviderOption> providers();
    java.util.List<PluginAiAgentOption> agents();
    CompletionStage<PluginAiChatResponse> runAgent(String agentCode, PluginAiChatRequest request);

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
