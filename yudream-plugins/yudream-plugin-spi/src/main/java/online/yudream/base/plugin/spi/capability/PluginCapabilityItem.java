package online.yudream.base.plugin.spi.capability;

import java.util.List;
import java.util.Map;

public record PluginCapabilityItem(
        String code,
        String name,
        String type,
        String description,
        String icon,
        Map<String, String> defaultConfig,
        List<String> dependencies
) {
    public PluginCapabilityItem {
        defaultConfig = defaultConfig == null ? Map.of() : Map.copyOf(defaultConfig);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
