package online.yudream.base.plugin.spi.ws;

/**
 * 插件 WebSocket 消息处理器。插件实现本接口并经
 * {@code PluginContext.registerWebSocketHandler(path, permission, handler)} 注册。
 *
 * <p>所有回调由宿主桥接调用；回调抛出的异常不会向客户端透传细节，
 * 宿主记录日志后按 {@link PluginWsCloseStatus#SERVER_ERROR} 关闭会话。
 * 消息文本编码为 UTF-8；二进制帧原样透传。
 */
public interface PluginWsHandler {

    /**
     * 连接建立（握手成功）后调用。宿主在握手阶段已完成鉴权与权限校验，
     * {@code handshake.principal()} 永不为 null；匿名连接的 userId 为 null。
     */
    void onOpen(PluginWsSession session, PluginWsHandshake handshake);

    /** 收到一条完整文本帧。 */
    default void onText(PluginWsSession session, String payload) {
    }

    /** 收到一条完整二进制帧。 */
    default void onBinary(PluginWsSession session, byte[] payload) {
    }

    /** 连接关闭（客户端断开、超时或任一方调用 close）后调用，每会话至多一次。 */
    default void onClose(PluginWsSession session, int statusCode, String reason) {
    }

    /** 传输层异常（发送失败、消息超限等）。宿主随后会关闭会话并回调 onClose。 */
    default void onError(PluginWsSession session, Throwable error) {
    }
}
