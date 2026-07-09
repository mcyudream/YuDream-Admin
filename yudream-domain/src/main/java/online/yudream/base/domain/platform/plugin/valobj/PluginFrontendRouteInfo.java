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
        String parentCode,
        Boolean visible,
        MenuStatus status,
        String menuCode,
        MenuNodeType type,
        String module,
        String link,
        String parentMenuCode,
        String parentParentCode,
        MenuNodeType parentType,
        String parentModule,
        String parentComponent,
        String parentLink,
        String parentPermission,
        Boolean parentVisible,
        MenuStatus parentStatus
) {
    public PluginFrontendRouteInfo(String path, String name, String title, String icon, String parentPath,
                                   String parentTitle, String parentIcon, Integer parentSort, String component,
                                   String permission, Integer sort, String parentCode, Boolean visible,
                                   MenuStatus status) {
        this(path, name, title, icon, parentPath, parentTitle, parentIcon, parentSort, component, permission, sort,
                parentCode, visible, status, null, MenuNodeType.MENU, null, null, null, null,
                MenuNodeType.LAYOUT, null, null, null, null, true, MenuStatus.ACTIVE);
    }

    public PluginFrontendRouteInfo(String path, String name, String title, String icon, String parentPath,
                                   String parentTitle, String parentIcon, Integer parentSort, String component,
                                   String permission, Integer sort) {
        this(path, name, title, icon, parentPath, parentTitle, parentIcon, parentSort, component, permission, sort,
                null, true, MenuStatus.ACTIVE);
    }

    public PluginFrontendRouteInfo {
        visible = visible == null || visible;
        status = status == null ? MenuStatus.ACTIVE : status;
        type = type == null ? MenuNodeType.MENU : type;
    }
}
