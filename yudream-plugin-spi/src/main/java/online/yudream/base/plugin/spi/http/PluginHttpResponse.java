package online.yudream.base.plugin.spi.http;

import java.util.Map;

public record PluginHttpResponse(
        int status,
        Map<String, String> headers,
        String contentType,
        Object body,
        boolean wrapped
) {
    public PluginHttpResponse(int status, Map<String, String> headers, String contentType, Object body) {
        this(status, headers, contentType, body, true);
    }

    public static PluginHttpResponse ok(Object body) {
        return json(200, body);
    }

    public static PluginHttpResponse json(int status, Object body) {
        return new PluginHttpResponse(status, Map.of(), "application/json", body, true);
    }

    public static PluginHttpResponse rawJson(int status, Object body) {
        return new PluginHttpResponse(status, Map.of(), "application/json", body, false);
    }

    public static PluginHttpResponse noContent() {
        return new PluginHttpResponse(204, Map.of(), "application/json", null, false);
    }

    public PluginHttpResponse withWrapped(boolean wrapped) {
        return new PluginHttpResponse(status, headers, contentType, body, wrapped);
    }

    public PluginHttpResponse {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        contentType = contentType == null || contentType.isBlank() ? "application/json" : contentType;
    }
}
