package online.yudream.base.interfaces.platform.plugin.ws;

/**
 * 插件 WebSocket 桥接固定路径与常量（最终冻结版本）。
 *
 * <p>WS 挂载在宿主专属前缀 {@code /api/platform/plugin-ws/{code}/**}：
 * 既不占用插件 HTTP 命名空间 {@code /api/plugins/{code}/**}（其 MVC
 * RequestMapping order=0 会压过任何标准 WS 映射），也不与宿主
 * {@code /api/platform/plugins/**} 形状重叠——因此插件 code 为任意合法值
 * （包括 "ws"、"assets"）时其既有 MVC 路由全部保持可达。
 * HandlerMapping order=-100 仅影响本前缀，非升级请求命中本前缀时 404。
 */
public final class PluginWsPaths {

    /** 握手路径前缀：/api/platform/plugin-ws/{pluginCode}/{pluginPath}。 */
    public static final String WS_PREFIX = "/api/platform/plugin-ws/";

    /** HandlerMapping 注册的 Ant 模式。 */
    public static final String WS_PATTERN = "/api/platform/plugin-ws/**";

    /** 本映射排序：先于 RequestMappingHandlerMapping（order=0），且只覆盖本前缀。 */
    public static final int MAPPING_ORDER = -100;

    /** 票据发放端点（已认证 HTTP POST），位于 WS 前缀之外，互不冲突。 */
    public static final String TICKET_PATH = "/api/platform/plugin-ws-ticket";

    /** 票据绑定的端点路径最大长度。 */
    public static final int MAX_ENDPOINT_PATH_LENGTH = 2048;

    private PluginWsPaths() {
    }
}
