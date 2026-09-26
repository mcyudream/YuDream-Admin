package online.yudream.base.plugin.spi.http;

/**
 * 流式 HTTP 端点处理器：经 {@code PluginContext.registerStreamingHttpHandler(method, path, handler)}
 * 或 {@code @PluginStreamingHttpEndpoint} 注册。请求体与响应支持真流式（不整体缓冲）；
 * 缓冲式端点继续使用 {@link PluginHttpHandler}，同一路径两者并存时流式优先。
 */
@FunctionalInterface
public interface PluginStreamingHttpHandler {

    /**
     * 处理流式请求。body 与 parts 在响应写出结束后由宿主统一关闭：处理器返回后、
     * 宿主写出流式响应期间它们仍可读取（支持请求转响应回显、part 回传）；
     * 返回缓冲式或 SSE 响应时在处理器返回后立即关闭。响应体如需流式，
     * 返回 body 为 {@link PluginHttpResponseBody} 的 {@link PluginHttpResponse}。
     */
    PluginHttpResponse handle(PluginStreamingHttpRequest request);
}
