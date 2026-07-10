package online.yudream.base.plugin.spi.system.messaging;

public record PluginMessageRequest(String connectionId, String platform, String userId, String channelId,
                                   PluginMessageContent content) {
}
