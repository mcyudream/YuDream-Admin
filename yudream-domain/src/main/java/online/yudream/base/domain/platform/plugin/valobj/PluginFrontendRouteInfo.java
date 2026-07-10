package online.yudream.base.domain.platform.plugin.valobj;

import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;

public record PluginFrontendRouteInfo(
        String path,
        String name,
        String title,
        String icon,
        String parentPath,
        String parentTitle,
        String parentIcon,
        Integer parentSort,
        String component,
        String permission,
        Integer sort,
        boolean hideInMenu,
        PluginMenuOverrideInfo menuOverride,
        PluginMenuOverrideInfo parentOverride
) {
    public PluginFrontendRouteInfo(String path, String name, String title, String icon, String parentPath,
                                   String parentTitle, String parentIcon, Integer parentSort, String component,
                                   String permission, Integer sort) {
        this(path, name, title, icon, parentPath, parentTitle, parentIcon, parentSort, component, permission, sort, false,
                null, null);
    }

    public PluginFrontendRouteInfo(String path, String name, String title, String icon, String parentPath,
                                   String parentTitle, String parentIcon, Integer parentSort, String component,
                                   String permission, Integer sort, boolean hideInMenu) {
        this(path, name, title, icon, parentPath, parentTitle, parentIcon, parentSort, component, permission, sort, hideInMenu,
                null, null);
    }

    public PluginFrontendRouteInfo(String path, String name, String title, String icon, String parentPath,
                                   String parentTitle, String parentIcon, Integer parentSort, String component,
                                   String permission, Integer sort, String parentCode, Boolean visible,
                                   MenuStatus status) {
        this(path, name, title, icon, parentPath, parentTitle, parentIcon, parentSort, component, permission, sort, false,
                PluginMenuOverrideInfo.builder()
                        .name(title)
                        .type(MenuNodeType.MENU)
                        .parentCode(parentCode)
                        .icon(icon)
                        .path(path)
                        .component(component)
                        .sort(sort)
                        .visible(visible)
                        .permission(permission)
                        .status(status)
                        .build(),
                parentPath == null ? null : PluginMenuOverrideInfo.builder()
                        .name(parentTitle)
                        .type(MenuNodeType.LAYOUT)
                        .icon(parentIcon)
                        .path(parentPath)
                        .sort(parentSort)
                        .build());
    }

    public PluginFrontendRouteInfo(String path, String name, String title, String icon, String parentPath,
                                   String parentTitle, String parentIcon, Integer parentSort, String component,
                                   String permission, Integer sort, String parentCode, Boolean visible,
                                   MenuStatus status, String menuCode, MenuNodeType type, String module, String link,
                                   String parentMenuCode, String parentParentCode, MenuNodeType parentType,
                                   String parentModule, String parentComponent, String parentLink,
                                   String parentPermission, Boolean parentVisible, MenuStatus parentStatus) {
        this(path, name, title, icon, parentPath, parentTitle, parentIcon, parentSort, component, permission, sort, false,
                PluginMenuOverrideInfo.builder()
                        .code(menuCode)
                        .name(title)
                        .type(type)
                        .parentCode(parentCode)
                        .module(module)
                        .icon(icon)
                        .path(path)
                        .component(component)
                        .link(link)
                        .sort(sort)
                        .visible(visible)
                        .permission(permission)
                        .status(status)
                        .build(),
                parentMenuCode == null ? null : PluginMenuOverrideInfo.builder()
                        .code(parentMenuCode)
                        .name(parentTitle)
                        .type(parentType)
                        .parentCode(parentParentCode)
                        .module(parentModule)
                        .icon(parentIcon)
                        .path(parentPath)
                        .component(parentComponent)
                        .link(parentLink)
                        .sort(parentSort)
                        .visible(parentVisible)
                        .permission(parentPermission)
                        .status(parentStatus)
                        .build());
    }

    public static PluginFrontendRouteInfo withMenuOverrides(PluginFrontendRouteInfo declaration,
                                                            PluginMenuOverrideInfo override,
                                                            PluginMenuOverrideInfo parentOverride) {
        return new PluginFrontendRouteInfo(
                override.path(), declaration.name(), override.name(), override.icon(),
                parentOverride == null ? null : parentOverride.path(),
                parentOverride == null ? null : parentOverride.name(),
                parentOverride == null ? null : parentOverride.icon(),
                parentOverride == null ? null : parentOverride.sort(),
                override.component(), override.permission(), override.sort(), declaration.hideInMenu(), override, parentOverride
        );
    }

    public PluginFrontendRouteInfo withSortOverrides(Integer overriddenParentSort, Integer overriddenSort) {
        return new PluginFrontendRouteInfo(
                path, name, title, icon, parentPath, parentTitle, parentIcon, overriddenParentSort,
                component, permission, overriddenSort, hideInMenu,
                menuOverride == null ? null : menuOverride.withSort(overriddenSort),
                parentOverride == null ? null : parentOverride.withSort(overriddenParentSort)
        );
    }

    public String parentCode() { return menuOverride == null ? null : menuOverride.parentCode(); }
    public Boolean visible() { return menuOverride == null ? true : menuOverride.visible(); }
    public MenuStatus status() { return menuOverride == null ? MenuStatus.ACTIVE : menuOverride.status(); }
    public String menuCode() { return menuOverride == null ? null : menuOverride.code(); }
    public MenuNodeType type() { return menuOverride == null ? MenuNodeType.MENU : menuOverride.type(); }
    public String module() { return menuOverride == null ? null : menuOverride.module(); }
    public String link() { return menuOverride == null ? null : menuOverride.link(); }
    public String parentMenuCode() { return parentOverride == null ? null : parentOverride.code(); }
    public String parentParentCode() { return parentOverride == null ? null : parentOverride.parentCode(); }
    public MenuNodeType parentType() { return parentOverride == null ? null : parentOverride.type(); }
    public String parentModule() { return parentOverride == null ? null : parentOverride.module(); }
    public String parentComponent() { return parentOverride == null ? null : parentOverride.component(); }
    public String parentLink() { return parentOverride == null ? null : parentOverride.link(); }
    public String parentPermission() { return parentOverride == null ? null : parentOverride.permission(); }
    public Boolean parentVisible() { return parentOverride == null ? null : parentOverride.visible(); }
    public MenuStatus parentStatus() { return parentOverride == null ? null : parentOverride.status(); }
}
