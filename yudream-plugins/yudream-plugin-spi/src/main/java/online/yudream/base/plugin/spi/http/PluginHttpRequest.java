package online.yudream.base.plugin.spi.http;

import online.yudream.base.plugin.spi.system.security.PluginPrincipal;

import java.util.List;
import java.util.Map;

public record PluginHttpRequest(
        String method,
        String path,
        Map<String, List<String>> headers,
        Map<String, List<String>> query,
        String body,
        Map<String, PluginHttpPart> parts,
        PluginPrincipal principal
) {
    public PluginHttpRequest {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        query = query == null ? Map.of() : Map.copyOf(query);
        parts = parts == null ? Map.of() : Map.copyOf(parts);
    }

    /** 兼容构造：非 multipart 请求（body 直传）。 */
    public PluginHttpRequest(
            String method,
            String path,
            Map<String, List<String>> headers,
            Map<String, List<String>> query,
            String body,
            PluginPrincipal principal
    ) {
        this(method, path, headers, query, body, Map.of(), principal);
    }
}
