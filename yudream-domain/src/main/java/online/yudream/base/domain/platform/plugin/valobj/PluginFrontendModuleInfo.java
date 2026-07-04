package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;

public record PluginFrontendModuleInfo(
        String pluginCode,
        String entry,
        String moduleName,
        String sdkVersion,
        String integrity,
        List<PluginFrontendRouteInfo> routes
) {
    public PluginFrontendModuleInfo {
        routes = routes == null ? List.of() : List.copyOf(routes);
    }
}
