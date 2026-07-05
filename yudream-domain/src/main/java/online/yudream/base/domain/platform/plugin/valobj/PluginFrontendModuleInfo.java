package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;

public record PluginFrontendModuleInfo(
        String pluginCode,
        String entry,
        String moduleName,
        String sdkVersion,
        String integrity,
        String menuTitle,
        String menuIcon,
        Integer menuSort,
        List<PluginFrontendRouteInfo> routes
) {
    public PluginFrontendModuleInfo(String pluginCode, String entry, String moduleName, String sdkVersion, String integrity,
                                    List<PluginFrontendRouteInfo> routes) {
        this(pluginCode, entry, moduleName, sdkVersion, integrity, "", "", 0, routes);
    }

    public PluginFrontendModuleInfo {
        menuSort = menuSort == null ? 0 : menuSort;
        routes = routes == null ? List.of() : List.copyOf(routes);
    }
}
