package online.yudream.base.plugin.spi.frontend;

public record PluginFrontendRoute(
        String path,
        String name,
        String title,
        String icon,
        String component,
        String permission,
        Integer sort
) {
}
