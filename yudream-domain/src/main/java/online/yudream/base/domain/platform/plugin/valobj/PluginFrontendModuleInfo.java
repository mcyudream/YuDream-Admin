package online.yudream.base.domain.platform.plugin.valobj;

import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;

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
        List<PluginFrontendRouteInfo> routes,
        String parentCode,
        Boolean visible,
        MenuStatus status,
        String menuCode,
        MenuNodeType menuType,
        String menuModule,
        String menuPath,
        String menuComponent,
        String menuLink,
        String menuPermission
) {
    public PluginFrontendModuleInfo(String pluginCode, String entry, String moduleName, String sdkVersion,
                                    String integrity, String menuTitle, String menuIcon, Integer menuSort,
                                    List<PluginFrontendRouteInfo> routes, String parentCode, Boolean visible,
                                    MenuStatus status) {
        this(pluginCode, entry, moduleName, sdkVersion, integrity, menuTitle, menuIcon, menuSort, routes,
                parentCode, visible, status, null, MenuNodeType.CATEGORY, null, null, null, null, null);
    }

    public PluginFrontendModuleInfo(String pluginCode, String entry, String moduleName, String sdkVersion,
                                    String integrity, String menuTitle, String menuIcon, Integer menuSort,
                                    List<PluginFrontendRouteInfo> routes) {
        this(pluginCode, entry, moduleName, sdkVersion, integrity, menuTitle, menuIcon, menuSort, routes,
                null, true, MenuStatus.ACTIVE);
    }

    public PluginFrontendModuleInfo(String pluginCode, String entry, String moduleName, String sdkVersion, String integrity,
                                    List<PluginFrontendRouteInfo> routes) {
        this(pluginCode, entry, moduleName, sdkVersion, integrity, "", "", 0, routes);
    }

    public PluginFrontendModuleInfo {
        menuSort = menuSort == null ? 0 : menuSort;
        routes = routes == null ? List.of() : List.copyOf(routes);
        visible = visible == null || visible;
        status = status == null ? MenuStatus.ACTIVE : status;
        menuType = menuType == null ? MenuNodeType.CATEGORY : menuType;
    }

    public PluginFrontendModuleInfo withSortOverrides(Integer overriddenMenuSort,
                                                      List<PluginFrontendRouteInfo> overriddenRoutes) {
        return new PluginFrontendModuleInfo(
                pluginCode, entry, moduleName, sdkVersion, integrity, menuTitle, menuIcon,
                overriddenMenuSort, overriddenRoutes, parentCode, visible, status, menuCode, menuType,
                menuModule, menuPath, menuComponent, menuLink, menuPermission
        );
    }
}
