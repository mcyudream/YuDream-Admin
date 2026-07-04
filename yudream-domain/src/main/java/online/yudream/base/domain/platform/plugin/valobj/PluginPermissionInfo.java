package online.yudream.base.domain.platform.plugin.valobj;

public record PluginPermissionInfo(
        String code,
        String name,
        String module,
        String description
) {
}
