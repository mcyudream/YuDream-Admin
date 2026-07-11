package online.yudream.base.plugin.spi.system.command;

public record PluginCommandInfo(String pluginCode, String code, String command, String name,
                                String permission, String description, boolean allowAnonymous) { }
