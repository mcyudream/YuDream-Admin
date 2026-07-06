package online.yudream.base.domain.platform.plugin.valobj;

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
        Integer sort
) {
}
