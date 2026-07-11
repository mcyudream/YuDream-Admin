package online.yudream.base.plugin.spi.system.messaging;

import java.util.concurrent.CompletionStage;

public interface PluginMessagingService {
    java.util.List<PluginMessagingConnection> connections();
    CompletionStage<PluginMessageResult> send(PluginMessageRequest request);

    /**
     * Opens a private conversation with the QQ bound to a system user and sends a message.
     * The host selects an enabled Satori connection; it rejects ambiguous configurations.
     */
    CompletionStage<PluginMessageResult> sendDirectToBoundUser(String userId, PluginMessageContent content);

    CompletionStage<PluginMessageResult> sendToChannel(String connectionId, String channelId, PluginMessageContent content);
}
