package online.yudream.base.plugin.spi.system.messaging;

import java.util.Set;

public record PluginInteractionFilter(Set<String> eventTypes, String platform, String channelId, String command) {
    public PluginInteractionFilter {
        eventTypes = eventTypes == null ? Set.of() : Set.copyOf(eventTypes);
    }
}
