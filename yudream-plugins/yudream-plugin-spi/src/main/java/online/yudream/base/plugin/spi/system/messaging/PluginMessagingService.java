package online.yudream.base.plugin.spi.system.messaging;

import java.util.concurrent.CompletionStage;

public interface PluginMessagingService {
    CompletionStage<PluginMessageResult> send(PluginMessageRequest request);
}
