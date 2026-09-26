package online.yudream.base.interfaces.platform.plugin.ws;

import java.net.URI;
import java.util.List;

/**
 * 插件 WebSocket 握手 Origin 策略。
 *
 * <p>规则：
 * <ul>
 *   <li>Origin 与直连请求（scheme/host/port）同源 → 放行；不读取 X-Forwarded-* / Forwarded，
 *       反代部署须通过 {@code yudream.plugin.ws.allowed-origins} 显式追加对外 Origin。</li>
 *   <li>Origin 缺失 → 仅允许 Authorization 头（非浏览器客户端）凭据通道；票据与匿名通道一律拒绝。</li>
 *   <li>Origin 存在但不匹配 → 拒绝。</li>
 * </ul>
 */
public class PluginWsOriginPolicy {

    private final List<String> allowedOrigins;

    public PluginWsOriginPolicy(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    /**
     * @param originHeader 请求 Origin 头（可空）
     * @param requestUri   直连请求的基准 URI（取 scheme/host/port）
     * @param headerCredential 是否使用 Authorization 头（含 API Key/OAuth/会话令牌）凭据
     */
    public boolean allows(String originHeader, URI requestUri, boolean headerCredential) {
        if (originHeader == null || originHeader.isBlank()) {
            return headerCredential;
        }
        URI origin = tryParse(originHeader.trim());
        if (origin == null || origin.getHost() == null || requestUri == null || requestUri.getHost() == null) {
            return false;
        }
        if (isSameOrigin(origin, requestUri)) {
            return true;
        }
        return allowedOrigins.contains(originHeader.trim());
    }

    private boolean isSameOrigin(URI origin, URI request) {
        if (!String.valueOf(origin.getScheme()).equalsIgnoreCase(request.getScheme())) {
            return false;
        }
        if (!origin.getHost().toLowerCase(java.util.Locale.ROOT)
                .equals(requestUriHost(request).toLowerCase(java.util.Locale.ROOT))) {
            return false;
        }
        return effectivePort(origin) == effectivePort(request);
    }

    private static String requestUriHost(URI request) {
        return request.getHost();
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static URI tryParse(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
