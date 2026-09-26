package online.yudream.base.interfaces.platform.plugin.ws;

import online.yudream.base.plugin.spi.ws.PluginWsHandler;

/**
 * 握手阶段解析出的插件 WS 端点绑定（桥接内部视图，与具体运行时网关类解耦）。
 */
public record PluginWsEndpointBinding(
        String pluginCode,
        String path,
        String permission,
        PluginWsHandler handler
) {
}
