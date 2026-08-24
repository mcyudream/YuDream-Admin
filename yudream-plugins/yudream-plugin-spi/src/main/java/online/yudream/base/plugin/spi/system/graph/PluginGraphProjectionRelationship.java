package online.yudream.base.plugin.spi.system.graph;

import java.util.LinkedHashMap;
import java.util.Map;

public record PluginGraphProjectionRelationship(String id, String type, String sourceId, String targetId,
                                                Map<String, Object> properties) {
    public PluginGraphProjectionRelationship {
        id = required(id, "Graph projection relationship id is required");
        type = required(type, "Graph projection relationship type is required");
        sourceId = required(sourceId, "Graph projection relationship source is required");
        targetId = required(targetId, "Graph projection relationship target is required");
        properties = properties == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(properties));
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
}
