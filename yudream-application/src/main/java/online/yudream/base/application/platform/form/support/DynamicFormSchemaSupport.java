package online.yudream.base.application.platform.form.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 动态表单 schema 解析支撑：识别上传字段、从提交值中解析文件 ID。
 */
public final class DynamicFormSchemaSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern FILE_URL_PATTERN = Pattern.compile("/api/files/(\\d+)/content");
    private static final Set<String> UPLOAD_TYPES = Set.of("upload", "fcupload");

    private DynamicFormSchemaSupport() {
    }

    public static Set<String> uploadFields(String schemaJson) {
        Set<String> fields = new LinkedHashSet<>();
        if (schemaJson == null || schemaJson.trim().isEmpty()) {
            return fields;
        }
        try {
            List<Map<String, Object>> rules = OBJECT_MAPPER.readValue(schemaJson, new TypeReference<>() {
            });
            collectUploadFields(rules, fields);
        } catch (Exception ignored) {
        }
        return fields;
    }

    public static List<Long> fileIds(Object value) {
        List<Long> ids = new ArrayList<>();
        collectFileIds(value, ids);
        return ids;
    }

    public static Long fileId(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = FILE_URL_PATTERN.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectUploadFields(List<Map<String, Object>> rules, Set<String> fields) {
        for (Map<String, Object> rule : rules) {
            Object type = rule.get("type");
            Object field = rule.get("field");
            if (type != null && UPLOAD_TYPES.contains(String.valueOf(type).toLowerCase())
                    && field != null && !String.valueOf(field).isBlank()) {
                fields.add(String.valueOf(field));
            }
            for (String childKey : List.of("children", "control")) {
                Object children = rule.get(childKey);
                if (children instanceof List<?> childList) {
                    collectUploadFields(childList.stream()
                            .filter(Map.class::isInstance)
                            .map(item -> (Map<String, Object>) item)
                            .toList(), fields);
                }
            }
        }
    }

    private static void collectFileIds(Object value, List<Long> ids) {
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> collectFileIds(item, ids));
            return;
        }
        if (value instanceof Map<?, ?>) {
            return;
        }
        Long id = value == null ? null : fileId(String.valueOf(value));
        if (id != null && !ids.contains(id)) {
            ids.add(id);
        }
    }
}
