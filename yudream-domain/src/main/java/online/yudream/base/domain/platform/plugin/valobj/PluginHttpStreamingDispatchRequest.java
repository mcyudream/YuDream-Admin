package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 流式 HTTP 分发请求（宿主内部值对象）。与缓冲式 {@link PluginHttpDispatchRequest} 相对：
 * query/body/parts 以惰性 Supplier 提供，运行时网关在完成鉴权与 Content-Length 前置限长之后
 * 才物化它们，确保表单解析、multipart 解析与请求体读取都不会先于鉴权/限长消费请求体。
 */
public final class PluginHttpStreamingDispatchRequest {

    private final String pluginCode;
    private final String method;
    private final String path;
    private final Map<String, List<String>> headers;
    private final Long userId;
    private final List<String> permissions;
    private final Supplier<Map<String, List<String>>> querySupplier;
    private final Supplier<PluginHttpStreamingBody> bodySupplier;
    private final Supplier<Map<String, PluginHttpStreamingPart>> partsSupplier;

    public PluginHttpStreamingDispatchRequest(
            String pluginCode,
            String method,
            String path,
            Map<String, List<String>> headers,
            Long userId,
            List<String> permissions,
            Supplier<Map<String, List<String>>> querySupplier,
            Supplier<PluginHttpStreamingBody> bodySupplier,
            Supplier<Map<String, PluginHttpStreamingPart>> partsSupplier
    ) {
        this.pluginCode = pluginCode;
        this.method = method;
        this.path = path;
        this.headers = headers == null ? Map.of() : Map.copyOf(headers);
        this.userId = userId;
        this.permissions = permissions == null ? List.of() : List.copyOf(permissions);
        this.querySupplier = querySupplier == null ? Map::of : querySupplier;
        this.bodySupplier = bodySupplier == null ? () -> null : bodySupplier;
        this.partsSupplier = partsSupplier == null ? Map::of : partsSupplier;
    }

    public String pluginCode() {
        return pluginCode;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public Map<String, List<String>> headers() {
        return headers;
    }

    public Long userId() {
        return userId;
    }

    public List<String> permissions() {
        return permissions;
    }

    /** 鉴权与前置限长通过后调用；可能触发 Servlet 表单解析。 */
    public Map<String, List<String>> query() {
        return querySupplier.get();
    }

    /** 鉴权与前置限长通过后调用；无 body 的请求返回 null。 */
    public PluginHttpStreamingBody body() {
        return bodySupplier.get();
    }

    /** 鉴权与前置限长通过后调用；可能触发 Servlet multipart 解析。 */
    public Map<String, PluginHttpStreamingPart> parts() {
        return partsSupplier.get();
    }
}
