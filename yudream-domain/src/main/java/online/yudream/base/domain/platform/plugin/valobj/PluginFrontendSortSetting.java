package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;

public record PluginFrontendSortSetting(
        String moduleName,
        Integer menuSort,
        List<PluginFrontendRouteSortSetting> routes
) {
    public PluginFrontendSortSetting {
        moduleName = moduleName == null ? "" : moduleName.trim();
        routes = routes == null ? List.of() : List.copyOf(routes);
    }

    public PluginFrontendRouteSortSetting routeSetting(PluginFrontendRouteInfo route) {
        if (route == null) {
            return null;
        }
        return routes.stream()
                .filter(setting -> setting.matches(route))
                .findFirst()
                .orElse(null);
    }
}
