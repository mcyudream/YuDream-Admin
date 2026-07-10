package online.yudream.base.plugin.spi.system.messaging;

public interface PluginMessageInteractionRegistry {
    AutoCloseable onMessage(PluginInteractionFilter filter, PluginMessageHandler handler);
    AutoCloseable onNative(PluginInteractionFilter filter, PluginMessageHandler handler);
    AutoCloseable onCommand(String command, PluginMessageHandler handler);
    AutoCloseable onButton(String buttonId, PluginMessageHandler handler);
    AutoCloseable beforeSend(PluginMessageHandler handler);
    AutoCloseable afterSend(PluginMessageHandler handler);
}
