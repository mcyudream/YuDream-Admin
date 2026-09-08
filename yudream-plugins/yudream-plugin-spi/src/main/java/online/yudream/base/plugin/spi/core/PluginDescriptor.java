package online.yudream.base.plugin.spi.core;

import java.util.List;

public record PluginDescriptor(
        String code,
        String name,
        String version,
        String description,
        String mainClass,
        List<String> dependencies,
        List<String> softDependencies,
        String icon
) {
    public PluginDescriptor(String code, String name, String version, String description,
                            String mainClass, List<String> dependencies, List<String> softDependencies) {
        this(code, name, version, description, mainClass, dependencies, softDependencies, null);
    }

    public PluginDescriptor {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        softDependencies = softDependencies == null ? List.of() : List.copyOf(softDependencies);
        icon = icon == null || icon.isBlank() ? null : icon.trim();
    }
}
