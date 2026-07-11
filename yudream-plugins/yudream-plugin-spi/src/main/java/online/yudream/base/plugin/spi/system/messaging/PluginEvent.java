package online.yudream.base.plugin.spi.system.messaging;

import java.util.Map;

public record PluginEvent(String sequence, String type, String platform, String userId, String channelId,
                          String content, String buttonId, String command, Map<String, Object> referrer,
                          String nativeType, Object nativeData, String connectionId, String selfId, String messageId) {
    public PluginEvent {
        referrer = referrer == null ? Map.of() : Map.copyOf(referrer);
    }
}
