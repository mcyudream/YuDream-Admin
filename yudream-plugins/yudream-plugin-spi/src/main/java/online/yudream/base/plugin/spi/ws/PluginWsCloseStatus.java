package online.yudream.base.plugin.spi.ws;

/**
 * WebSocket 关闭码常量（RFC 6455 常用子集）。
 */
public final class PluginWsCloseStatus {

    /** 正常关闭。 */
    public static final int NORMAL = 1000;

    /** 端点离开（页面关闭等）。 */
    public static final int GOING_AWAY = 1001;

    /** 违反策略（如未授权、被插件拒绝）。 */
    public static final int VIOLATED_POLICY = 1008;

    /** 消息超过大小限制。 */
    public static final int MESSAGE_TOO_BIG = 1009;

    /** 服务端内部错误（插件回调异常等）。 */
    public static final int SERVER_ERROR = 1011;

    private PluginWsCloseStatus() {
    }
}
