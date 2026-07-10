package online.yudream.base.plugin.spi.system.messaging;

@FunctionalInterface
public interface PluginMessageHandler {
    void handle(PluginEvent event) throws Exception;
}
