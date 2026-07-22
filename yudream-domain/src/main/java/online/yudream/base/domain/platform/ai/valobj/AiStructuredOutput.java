package online.yudream.base.domain.platform.ai.valobj;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AiStructuredOutput(
        Mode mode,
        String name,
        Map<String, Object> schema,
        boolean strict
) {
    public enum Mode {
        NONE,
        JSON_OBJECT,
        JSON_SCHEMA
    }

    public AiStructuredOutput {
        mode = mode == null ? Mode.NONE : mode;
        name = name == null ? "" : name.trim();
        schema = schema == null || schema.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(schema));
        if (mode == Mode.JSON_SCHEMA && schema.isEmpty()) {
            throw new IllegalArgumentException("JSON Schema structured output requires a schema");
        }
    }

    public static AiStructuredOutput none() {
        return new AiStructuredOutput(Mode.NONE, "", Map.of(), false);
    }

    public static AiStructuredOutput jsonObject() {
        return new AiStructuredOutput(Mode.JSON_OBJECT, "", Map.of(), false);
    }

    public static AiStructuredOutput jsonSchema(String name, Map<String, Object> schema, boolean strict) {
        return new AiStructuredOutput(Mode.JSON_SCHEMA, name, schema, strict);
    }

    public boolean enabled() {
        return mode != Mode.NONE;
    }
}
