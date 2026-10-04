package online.yudream.base.infra.platform.ai.runtime;

import cn.hutool.json.JSONUtil;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;

import java.util.Map;

/**
 * 平台工具描述符 → 模型侧 JSON Schema 的归一化：
 * ① inputSchemaDefinition 优先原样透传；② 含 properties 键的完整 JSON Schema 原样透传
 * （插件工具可直接声明完整 schema）；③ 其余按「属性名 → 描述」简写包装成 object schema。
 */
public final class ToolSchemas {

    private ToolSchemas() {
    }

    public static Map<String, Object> normalize(AiAgentToolDescriptor descriptor) {
        Map<String, Object> definition = descriptor.inputSchemaDefinition();
        if (definition != null && !definition.isEmpty()) {
            return definition;
        }
        Map<String, Object> schema = descriptor.inputSchema();
        if (schema == null || schema.isEmpty()) {
            return Map.of("type", "object", "additionalProperties", true, "properties", Map.of());
        }
        if (schema.get("properties") instanceof Map) {
            return schema;
        }
        Map<String, Object> properties = new java.util.LinkedHashMap<>();
        schema.forEach((key, value) -> properties.put(key, propertySchema(value)));
        Map<String, Object> wrapped = new java.util.LinkedHashMap<>();
        wrapped.put("type", "object");
        wrapped.put("additionalProperties", true);
        wrapped.put("properties", properties);
        return wrapped;
    }

    public static Map<String, Object> normalizeSimple(Map<String, Object> inputSchema) {
        if (inputSchema == null || inputSchema.isEmpty()) {
            return Map.of("type", "object", "additionalProperties", true, "properties", Map.of());
        }
        if (inputSchema.get("properties") instanceof Map) {
            return inputSchema;
        }
        Map<String, Object> properties = new java.util.LinkedHashMap<>();
        inputSchema.forEach((key, value) -> properties.put(key, propertySchema(value)));
        Map<String, Object> wrapped = new java.util.LinkedHashMap<>();
        wrapped.put("type", "object");
        wrapped.put("additionalProperties", true);
        wrapped.put("properties", properties);
        return wrapped;
    }

    private static Map<String, Object> propertySchema(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> property = new java.util.LinkedHashMap<>();
            map.forEach((key, item) -> property.put(String.valueOf(key), item));
            property.putIfAbsent("type", "string");
            return property;
        }
        Map<String, Object> property = new java.util.LinkedHashMap<>();
        property.put("type", "string");
        property.put("description", value == null ? "" : String.valueOf(value));
        return property;
    }

    /**
     * 模型侧工具名只保留安全字符；平台侧仍用原始 descriptor.name 关联。
     */
    public static String safeName(String name) {
        return name == null ? "tool" : name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /**
     * 工具结果序列化给模型：统一用 Jackson（Hutool 不认 record 访问器会输出 {}）。
     */
    public static String resultJson(Object value) {
        try {
            return ToolResultJson.MAPPER.writeValueAsString(value);
        } catch (Exception exception) {
            return String.valueOf(value);
        }
    }

    public static String pretty(Map<String, Object> schema) {
        try {
            return JSONUtil.toJsonStr(schema);
        } catch (Exception e) {
            return String.valueOf(schema);
        }
    }

    private static final class ToolResultJson {
        private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
                new com.fasterxml.jackson.databind.ObjectMapper();
    }
}
