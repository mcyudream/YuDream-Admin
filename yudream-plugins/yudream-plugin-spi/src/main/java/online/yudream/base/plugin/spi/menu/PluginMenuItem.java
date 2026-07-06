package online.yudream.base.plugin.spi.menu;

public record PluginMenuItem(
        String title,
        String path,
        String icon,
        String permission,
        String parentPath,
        Integer sort
) {
}
