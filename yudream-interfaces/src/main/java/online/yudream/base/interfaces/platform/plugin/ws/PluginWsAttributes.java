package online.yudream.base.interfaces.platform.plugin.ws;

/**
 * 握手拦截器与桥接处理器之间通过 WebSocketSession attributes 传递的数据键。
 */
public final class PluginWsAttributes {

    /** 解析出的端点绑定（PluginWsEndpointBinding）。 */
    public static final String BINDING = "pluginWs.binding";

    /** 握手主体（PluginPrincipal，永不为 null）。 */
    public static final String PRINCIPAL = "pluginWs.principal";

    /** 插件内相对路径（String）。 */
    public static final String PATH = "pluginWs.path";

    /** 查询参数（Map&lt;String, List&lt;String&gt;&gt;）。 */
    public static final String QUERY = "pluginWs.query";

    /** 请求头（Map&lt;String, List&lt;String&gt;&gt;）。 */
    public static final String HEADERS = "pluginWs.headers";

    private PluginWsAttributes() {
    }
}
