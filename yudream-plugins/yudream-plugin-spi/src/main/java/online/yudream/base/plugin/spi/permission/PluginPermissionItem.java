package online.yudream.base.plugin.spi.permission;

public record PluginPermissionItem(
        String code,
        String name,
        String module,
        String description
) {
}
