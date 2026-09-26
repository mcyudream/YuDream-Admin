package online.yudream.base.interfaces.platform.plugin.request;

import jakarta.validation.constraints.NotBlank;

/**
 * WebSocket 握手票据发放请求：票据强绑定插件 code 与规范化端点路径。
 */
public record PluginWsTicketRequest(
        @NotBlank(message = "插件 code 不能为空") String pluginCode,
        @NotBlank(message = "端点路径不能为空") String endpointPath
) {
}
