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
        List<String> softDependencies
) {
    public PluginDescriptorInfo {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        softDependencies = softDependencies == null ? List.of() : List.copyOf(softDependencies);
    }
}
