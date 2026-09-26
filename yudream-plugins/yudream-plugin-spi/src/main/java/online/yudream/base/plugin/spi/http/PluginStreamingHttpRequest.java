package online.yudream.base.plugin.spi.http;

import online.yudream.base.plugin.spi.system.security.PluginPrincipal;

import java.util.List;
import java.util.Map;

/**
 * 流式 HTTP 请求：与 {@link PluginHttpRequest} 相对，body 与 multipart part 不做整体缓冲，
 * 只提供给注册为流式的端点（见 {@link PluginStreamingHttpHandler}）。
 */
public record PluginStreamingHttpRequest(
        String method,
        String path,
        Map<String, List<String>> headers,
        Map<String, List<String>> query,
        PluginHttpBodyStream body,
        Map<String, PluginHttpStreamingPart> parts,
        PluginPrincipal principal
) {
    public PluginStreamingHttpRequest {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        query = query == null ? Map.of() : Map.copyOf(query);
        parts = parts == null ? Map.of() : Map.copyOf(parts);
    }
}
