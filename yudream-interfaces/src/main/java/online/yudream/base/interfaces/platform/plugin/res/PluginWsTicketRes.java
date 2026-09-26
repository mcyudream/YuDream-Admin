package online.yudream.base.interfaces.platform.plugin.res;

/**
 * WebSocket 握手票据发放结果。
 */
public record PluginWsTicketRes(
        String ticket,
        long expiresIn
) {
}
