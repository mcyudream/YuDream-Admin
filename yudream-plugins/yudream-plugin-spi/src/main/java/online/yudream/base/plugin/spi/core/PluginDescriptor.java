package online.yudream.base.plugin.spi.core;

import java.util.List;

public record PluginDescriptor(
        String code,
        String name,
        String version,
        String description,
        String mainClass,
        List<String> dependencies
) {
    public PluginDescriptor {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
