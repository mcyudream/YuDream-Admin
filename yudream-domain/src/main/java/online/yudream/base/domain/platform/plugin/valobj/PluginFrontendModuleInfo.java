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
        PluginMenuOverrideInfo menuOverride
) {
    public PluginFrontendModuleInfo(String pluginCode, String entry, String moduleName, String sdkVersion,
                                    String integrity, String menuTitle, String menuIcon, Integer menuSort,
                                    List<PluginFrontendRouteInfo> routes) {
        this(pluginCode, entry, moduleName, sdkVersion, integrity, menuTitle, menuIcon, menuSort, routes, null);
    }

    public PluginFrontendModuleInfo(String pluginCode, String entry, String moduleName, String sdkVersion,
                                    String integrity, List<PluginFrontendRouteInfo> routes) {
        this(pluginCode, entry, moduleName, sdkVersion, integrity, "", "", 0, routes);
    }

    public PluginFrontendModuleInfo(String pluginCode, String entry, String moduleName, String sdkVersion,
                                    String integrity, String menuTitle, String menuIcon, Integer menuSort,
                                    List<PluginFrontendRouteInfo> routes, String parentCode, Boolean visible,
                                    MenuStatus status) {
        this(pluginCode, entry, moduleName, sdkVersion, integrity, menuTitle, menuIcon, menuSort, routes,
                PluginMenuOverrideInfo.builder()
                        .name(menuTitle)
                        .type(MenuNodeType.CATEGORY)
                        .parentCode(parentCode)
                        .icon(menuIcon)
                        .sort(menuSort)
                        .visible(visible)
                        .status(status)
                        .build());
    }

    public PluginFrontendModuleInfo(String pluginCode, String entry, String moduleName, String sdkVersion,
                                    String integrity, String menuTitle, String menuIcon, Integer menuSort,
                                    List<PluginFrontendRouteInfo> routes, String parentCode, Boolean visible,
                                    MenuStatus status, String menuCode, MenuNodeType menuType, String menuModule,
                                    String menuPath, String menuComponent, String menuLink, String menuPermission) {
        this(pluginCode, entry, moduleName, sdkVersion, integrity, menuTitle, menuIcon, menuSort, routes,
                PluginMenuOverrideInfo.builder()
                        .code(menuCode)
                        .name(menuTitle)
                        .type(menuType)
                        .parentCode(parentCode)
                        .module(menuModule)
                        .icon(menuIcon)
                        .path(menuPath)
                        .component(menuComponent)
                        .link(menuLink)
                        .sort(menuSort)
                        .visible(visible)
                        .permission(menuPermission)
                        .status(status)
                        .build());
    }

    public PluginFrontendModuleInfo {
        menuSort = menuSort == null ? 0 : menuSort;
        routes = routes == null ? List.of() : List.copyOf(routes);
    }

    public static PluginFrontendModuleInfo withMenuOverride(PluginFrontendModuleInfo declaration,
                                                            PluginMenuOverrideInfo override,
                                                            List<PluginFrontendRouteInfo> routes) {
        return new PluginFrontendModuleInfo(
                declaration.pluginCode(), declaration.entry(), declaration.moduleName(), declaration.sdkVersion(),
                declaration.integrity(), override.name(), override.icon(), override.sort(), routes, override
        );
    }

    public PluginFrontendModuleInfo withSortOverrides(Integer overriddenMenuSort,
                                                      List<PluginFrontendRouteInfo> overriddenRoutes) {
        return new PluginFrontendModuleInfo(
                pluginCode, entry, moduleName, sdkVersion, integrity, menuTitle, menuIcon,
                overriddenMenuSort, overriddenRoutes,
                menuOverride == null ? null : menuOverride.withSort(overriddenMenuSort)
        );
    }

    public String parentCode() { return menuOverride == null ? null : menuOverride.parentCode(); }
    public Boolean visible() { return menuOverride == null ? true : menuOverride.visible(); }
    public MenuStatus status() { return menuOverride == null ? MenuStatus.ACTIVE : menuOverride.status(); }
    public String menuCode() { return menuOverride == null ? null : menuOverride.code(); }
    public MenuNodeType menuType() { return menuOverride == null ? MenuNodeType.CATEGORY : menuOverride.type(); }
    public String menuModule() { return menuOverride == null ? null : menuOverride.module(); }
    public String menuPath() { return menuOverride == null ? null : menuOverride.path(); }
    public String menuComponent() { return menuOverride == null ? null : menuOverride.component(); }
    public String menuLink() { return menuOverride == null ? null : menuOverride.link(); }
    public String menuPermission() { return menuOverride == null ? null : menuOverride.permission(); }
}
