package online.yudream.base.domain.platform.graph.valobj;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record GraphProjectionNode(String id, String type, Map<String, Object> properties) {
    public GraphProjectionNode {
        id = required(id, "图投影节点 ID 不能为空");
        type = required(type, "图投影节点类型不能为空");
        properties = properties == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
}
