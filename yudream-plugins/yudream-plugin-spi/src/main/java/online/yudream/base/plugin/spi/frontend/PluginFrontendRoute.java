package online.yudream.base.plugin.spi.frontend;

public record PluginFrontendRoute(
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
        boolean hideInMenu
) {
}
