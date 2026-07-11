package online.yudream.base.plugin.spi.system.messaging;

import java.util.concurrent.CompletionStage;

public interface PluginMessagingService {
    CompletionStage<PluginMessageResult> send(PluginMessageRequest request);

    /**
     * Opens a private conversation with the QQ bound to a system user and sends a message.
     * The host selects an enabled Satori connection; it rejects ambiguous configurations.
     */
    CompletionStage<PluginMessageResult> sendDirectToBoundUser(String userId, PluginMessageContent content);
}
