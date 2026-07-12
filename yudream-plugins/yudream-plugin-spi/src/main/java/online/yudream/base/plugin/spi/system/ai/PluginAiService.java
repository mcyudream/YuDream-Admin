package online.yudream.base.plugin.spi.system.ai;

import java.util.concurrent.CompletionStage;

public interface PluginAiService {
    CompletionStage<PluginAiChatResponse> chat(PluginAiChatRequest request);
    java.util.List<PluginAiToolDescriptor> tools();
    java.util.List<PluginAiProviderOption> providers();
}
