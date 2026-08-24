package online.yudream.base.domain.platform.graph.valobj;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record GraphProjectionRelationship(String id, String type, String sourceId, String targetId,
                                          Map<String, Object> properties) {
    public GraphProjectionRelationship {
        id = required(id, "图投影关系 ID 不能为空");
        type = required(type, "图投影关系类型不能为空");
        sourceId = required(sourceId, "图投影关系起点不能为空");
        targetId = required(targetId, "图投影关系终点不能为空");
        properties = properties == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
}
