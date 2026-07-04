package online.yudream.base.domain.platform.plugin.valobj;

public record PluginFrontendRouteInfo(
        String path,
        String name,
        String title,
        String icon,
        String component,
        String permission,
        Integer sort
) {
}
