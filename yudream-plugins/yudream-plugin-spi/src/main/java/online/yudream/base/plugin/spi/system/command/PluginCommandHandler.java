package online.yudream.base.plugin.spi.system.command;

@FunctionalInterface
public interface PluginCommandHandler {
    void handle(PluginCommandContext context) throws Exception;
}
