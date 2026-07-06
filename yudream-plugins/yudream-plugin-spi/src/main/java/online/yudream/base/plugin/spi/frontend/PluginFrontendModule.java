package online.yudream.base.plugin.spi.frontend;

import java.util.List;

public record PluginFrontendModule(
        String entry,
        String moduleName,
        String sdkVersion,
        String integrity,
        String menuTitle,
        String menuIcon,
        Integer menuSort,
        List<PluginFrontendRoute> routes
) {
    public PluginFrontendModule(String entry, String moduleName, String sdkVersion, String integrity, List<PluginFrontendRoute> routes) {
        this(entry, moduleName, sdkVersion, integrity, "", "", 0, routes);
    }

    public PluginFrontendModule {
        menuSort = menuSort == null ? 0 : menuSort;
        routes = routes == null ? List.of() : List.copyOf(routes);
    }
}
