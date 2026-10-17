package online.yudream.base.domain.platform.ai.valobj;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具输入 schema 归一化：把平台工具的「属性名 → 描述」简写包装成标准 JSON Schema
 * （type=object + properties），管理端工具目录与运行期模型侧共用同一换算，
 * 保证插件/系统工具声明简写时前端也能生成参数骨架。
 */
public final class AiAgentToolSchemas {

    private AiAgentToolSchemas() {
    }

    /** 归一化任意输入 schema：已是完整 JSON Schema 时原样返回。 */
    public static Map<String, Object> normalize(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return Map.of("type", "object", "additionalProperties", true, "properties", Map.of());
        }
        if (schema.get("properties") instanceof Map) {
            return schema;
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        schema.forEach((key, value) -> properties.put(key, propertySchema(value)));
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("type", "object");
        wrapped.put("additionalProperties", true);
        wrapped.put("properties", properties);
        return wrapped;
    }

    /** 归一化工具描述符的 schema：完整定义优先。 */
    public static Map<String, Object> normalize(AiAgentToolDescriptor descriptor) {
        if (descriptor == null) {
            return normalize((Map<String, Object>) null);
        }
        Map<String, Object> definition = descriptor.inputSchemaDefinition();
        if (definition != null && !definition.isEmpty()) {
            return definition;
        }
        return normalize(descriptor.inputSchema());
    }

    private static Map<String, Object> propertySchema(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> property = new LinkedHashMap<>();
            map.forEach((key, item) -> property.put(String.valueOf(key), item));
            property.putIfAbsent("type", "string");
            return property;
        }
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "string");
        property.put("description", value == null ? "" : String.valueOf(value));
        return property;
    }
}
