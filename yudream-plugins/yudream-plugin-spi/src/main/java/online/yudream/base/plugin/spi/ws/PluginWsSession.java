package online.yudream.base.plugin.spi.ws;

import online.yudream.base.plugin.spi.system.security.PluginPrincipal;

/**
 * 插件 WebSocket 会话的出站 API。宿主实现；发送为非阻塞入队，
 * 底层发送超时或缓冲超限时宿主强制关闭会话并回调 {@link PluginWsHandler#onClose}。
 */
public interface PluginWsSession {

    /** 会话标识（底层容器连接 ID，同一连接生命周期内稳定）。 */
    String id();

    /** 所属插件 code。 */
    String pluginCode();

    /** 握手时解析出的安全主体；匿名连接的 userId 为 null、permissions 为空。 */
    PluginPrincipal principal();

    /** 会话是否仍处于打开状态。 */
    boolean isOpen();

    /** 发送一条文本帧。队列已满或会话已关闭时抛出 IllegalStateException。 */
    void sendText(String payload);

    /** 发送一条二进制帧。队列已满或会话已关闭时抛出 IllegalStateException。 */
    void sendBinary(byte[] payload);

    /** 主动关闭会话；statusCode 建议使用 {@link PluginWsCloseStatus} 常量。重复调用静默忽略。 */
    void close(int statusCode, String reason);
}
