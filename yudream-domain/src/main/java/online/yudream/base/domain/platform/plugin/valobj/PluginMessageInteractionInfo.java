package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;

public record PluginMessageInteractionInfo(
        String pluginCode,
        String kind,
        List<String> eventTypes,
        String platform,
        String channelId,
        String command
) {
    public PluginMessageInteractionInfo {
        eventTypes = eventTypes == null ? List.of() : List.copyOf(eventTypes);
    }
}
