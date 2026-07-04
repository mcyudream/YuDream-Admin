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
        PluginPrincipal principal
) {
    public PluginHttpRequest {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        query = query == null ? Map.of() : Map.copyOf(query);
    }
}
