package online.yudream.base.domain.platform.plugin.valobj;

import java.util.Map;

public record PluginHttpDispatchResult(
        int status,
        Map<String, String> headers,
        String contentType,
        Object body,
        boolean wrapped
) {
    public PluginHttpDispatchResult(int status, Map<String, String> headers, String contentType, Object body) {
        this(status, headers, contentType, body, true);
    }

    public PluginHttpDispatchResult {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        contentType = contentType == null || contentType.isBlank() ? "application/json" : contentType;
    }
}
