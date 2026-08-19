package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;

public record PluginCapabilityAssetInfo(
        String code,
        String name,
        String type,
        String description,
        String icon,
        List<String> dependencies
) {
    public PluginCapabilityAssetInfo {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
