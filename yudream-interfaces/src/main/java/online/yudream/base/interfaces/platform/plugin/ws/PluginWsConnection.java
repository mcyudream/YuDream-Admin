package online.yudream.base.interfaces.platform.plugin.ws;

import online.yudream.base.plugin.spi.ws.PluginWsHandler;
import online.yudream.base.plugin.spi.ws.PluginWsSession;

/**
 * 一条活跃连接：绑定信息 + 会话适配器。
 */
public record PluginWsConnection(PluginWsEndpointBinding binding, PluginWsSessionAdapter adapter) {

    public PluginWsHandler handler() {
        return binding.handler();
    }

    public PluginWsSession session() {
        return adapter;
    }

    public String pluginCode() {
        return binding.pluginCode();
    }
}
