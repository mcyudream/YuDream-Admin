package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;
import java.util.Map;

public record PluginHttpDispatchRequest(
        String pluginCode,
        String method,
        String path,
        Map<String, List<String>> headers,
        Map<String, List<String>> query,
        String body,
        Long userId,
        List<String> permissions
) {
    public PluginHttpDispatchRequest {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        query = query == null ? Map.of() : Map.copyOf(query);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
