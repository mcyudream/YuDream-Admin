package online.yudream.base.plugin.spi.system.command;

public record PluginCommandDefinition(String code, String command, String name, String permission,
                                      String description, boolean allowAnonymous) {
}
