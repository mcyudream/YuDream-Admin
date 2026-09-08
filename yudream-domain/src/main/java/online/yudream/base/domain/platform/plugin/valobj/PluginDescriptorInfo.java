package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;

public record PluginDescriptorInfo(
        String code,
        String name,
        String version,
        String description,
        String mainClass,
        String jarPath,
        List<String> dependencies,
        List<String> softDependencies,
        String icon
) {
    public PluginDescriptorInfo(String code, String name, String version, String description,
                                String mainClass, String jarPath, List<String> dependencies,
                                List<String> softDependencies) {
        this(code, name, version, description, mainClass, jarPath, dependencies, softDependencies, null);
    }

    public PluginDescriptorInfo {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        softDependencies = softDependencies == null ? List.of() : List.copyOf(softDependencies);
        icon = icon == null || icon.isBlank() ? null : icon.trim();
    }
}
