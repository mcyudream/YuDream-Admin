package online.yudream.base.domain.platform.plugin.valobj;

public record PluginCommandInfo(String pluginCode, String code, String command, String name,
                                String permission, String description, boolean allowAnonymous) {
}
