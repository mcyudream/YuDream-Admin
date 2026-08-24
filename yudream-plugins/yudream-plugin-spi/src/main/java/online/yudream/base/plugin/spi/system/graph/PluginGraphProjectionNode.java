package online.yudream.base.plugin.spi.system.graph;

import java.util.LinkedHashMap;
import java.util.Map;

public record PluginGraphProjectionNode(String id, String type, Map<String, Object> properties) {
    public PluginGraphProjectionNode {
        id = required(id, "Graph projection node id is required");
        type = required(type, "Graph projection node type is required");
        properties = properties == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(properties));
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
}
