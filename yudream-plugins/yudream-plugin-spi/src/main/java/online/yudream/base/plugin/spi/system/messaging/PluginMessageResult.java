package online.yudream.base.plugin.spi.system.messaging;

import java.util.List;

public record PluginMessageResult(List<String> messageIds, boolean rendered, boolean degraded) {
    public PluginMessageResult {
        messageIds = messageIds == null ? List.of() : List.copyOf(messageIds);
    }
}
