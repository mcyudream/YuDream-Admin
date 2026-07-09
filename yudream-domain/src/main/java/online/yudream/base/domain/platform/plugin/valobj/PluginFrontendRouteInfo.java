package online.yudream.base.domain.platform.plugin.valobj;

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
        String parentCode,
        Boolean visible,
        MenuStatus status
) {
    public PluginFrontendRouteInfo(String path, String name, String title, String icon, String parentPath,
                                   String parentTitle, String parentIcon, Integer parentSort, String component,
                                   String permission, Integer sort) {
        this(path, name, title, icon, parentPath, parentTitle, parentIcon, parentSort, component, permission, sort,
                null, true, MenuStatus.ACTIVE);
    }

    public PluginFrontendRouteInfo {
        visible = visible == null || visible;
        status = status == null ? MenuStatus.ACTIVE : status;
    }
}
