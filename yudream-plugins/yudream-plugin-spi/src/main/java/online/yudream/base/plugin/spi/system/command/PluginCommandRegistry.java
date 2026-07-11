package online.yudream.base.plugin.spi.system.command;

public interface PluginCommandRegistry {
    AutoCloseable register(PluginCommandDefinition definition, PluginCommandHandler handler);
}
