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
        String parentCode,
        List<String> styles,
        List<String> scripts,
        List<PluginFrontendRoute> routes
) {
    public PluginFrontendModule(String entry, String moduleName, String sdkVersion, String integrity, List<PluginFrontendRoute> routes) {
        this(entry, moduleName, sdkVersion, integrity, "", "", 0, "", List.of(), List.of(), routes);
    }

    public PluginFrontendModule(String entry, String moduleName, String sdkVersion, String integrity,
                                String menuTitle, String menuIcon, Integer menuSort,
                                List<PluginFrontendRoute> routes) {
        this(entry, moduleName, sdkVersion, integrity, menuTitle, menuIcon, menuSort, "", List.of(), List.of(), routes);
    }

    public PluginFrontendModule(String entry, String moduleName, String sdkVersion, String integrity,
                                String menuTitle, String menuIcon, Integer menuSort, String parentCode,
                                List<PluginFrontendRoute> routes) {
        this(entry, moduleName, sdkVersion, integrity, menuTitle, menuIcon, menuSort, parentCode, List.of(), List.of(), routes);
    }

    public PluginFrontendModule {
        menuSort = menuSort == null ? 0 : menuSort;
        styles = styles == null ? List.of() : List.copyOf(styles);
        scripts = scripts == null ? List.of() : List.copyOf(scripts);
        routes = routes == null ? List.of() : List.copyOf(routes);
    }
}
