package online.yudream.base.interfaces.platform.form.assembler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.form.dto.DynamicFormDTO;
import online.yudream.base.application.platform.form.dto.FormSubmissionDTO;
import online.yudream.base.application.platform.form.dto.FormSubmissionExportDTO;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class FormSubmissionExcelAssembler {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FormSubmissionExcelAssembler() {
    }

    public static String filename(FormSubmissionExportDTO export) {
        DynamicFormDTO form = export.getForm();
        String name = form == null || form.getName() == null ? "表单提交结果" : form.getName();
        return sanitizeFilename(name + "-提交结果");
    }

    public static List<List<String>> head(FormSubmissionExportDTO export) {
        List<List<String>> head = new ArrayList<>();
        head.add(List.of("提交 ID"));
        for (FormField field : fields(export)) {
            head.add(List.of(field.title()));
        }
        head.add(List.of("提交用户"));
        head.add(List.of("来源 IP"));
        head.add(List.of("提交时间"));
        return head;
    }

    public static List<List<Object>> rows(FormSubmissionExportDTO export) {
        List<FormField> fields = fields(export);
        return safeSubmissions(export).stream()
                .map(submission -> row(submission, fields))
                .toList();
    }

    private static List<Object> row(FormSubmissionDTO submission, List<FormField> fields) {
        Map<String, Object> data = submission.getData() == null ? Map.of() : submission.getData();
        List<Object> row = new ArrayList<>();
        row.add(submission.getId());
        for (FormField field : fields) {
            row.add(valueText(data.get(field.field())));
        }
        row.add(submission.getSubmitterId());
        row.add(submission.getSubmitterIp());
        row.add(dateText(submission.getSubmittedAt()));
        return row;
    }

    private static List<FormField> fields(FormSubmissionExportDTO export) {
        DynamicFormDTO form = export.getForm();
        Map<String, String> fields = parseSchemaFields(form == null ? null : form.getSchemaJson());
        for (FormSubmissionDTO submission : safeSubmissions(export)) {
            Map<String, Object> data = submission.getData() == null ? Map.of() : submission.getData();
            data.keySet().forEach(key -> fields.putIfAbsent(key, key));
        }
        return fields.entrySet().stream()
                .map(entry -> new FormField(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static Map<String, String> parseSchemaFields(String schemaJson) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (schemaJson == null || schemaJson.trim().isEmpty()) {
            return fields;
        }
        try {
            List<Map<String, Object>> rules = OBJECT_MAPPER.readValue(schemaJson, new TypeReference<>() {
            });
            collectFields(rules, fields);
        } catch (Exception ignored) {
        }
        return fields;
    }

    @SuppressWarnings("unchecked")
    private static void collectFields(List<Map<String, Object>> rules, Map<String, String> fields) {
        for (Map<String, Object> rule : rules) {
            Object field = rule.get("field");
            if (field != null && !String.valueOf(field).isBlank()) {
                fields.putIfAbsent(String.valueOf(field), title(rule, String.valueOf(field)));
            }
            for (String childKey : List.of("children", "control")) {
                Object children = rule.get(childKey);
                if (children instanceof List<?> childList) {
                    collectFields(childList.stream()
                            .filter(Map.class::isInstance)
                            .map(item -> (Map<String, Object>) item)
                            .toList(), fields);
                }
            }
        }
    }

    private static String title(Map<String, Object> rule, String fallback) {
        Object title = rule.get("title");
        return title == null || String.valueOf(title).isBlank() ? fallback : String.valueOf(title);
    }

    private static List<FormSubmissionDTO> safeSubmissions(FormSubmissionExportDTO export) {
        return export.getSubmissions() == null ? List.of() : export.getSubmissions();
    }

    private static String valueText(Object value) {
        if (value == null || Objects.equals(value, "")) {
            return "";
        }
        if (value instanceof Iterable<?> iterable) {
            return java.util.stream.StreamSupport.stream(iterable.spliterator(), false)
                    .map(FormSubmissionExcelAssembler::valueText)
                    .collect(Collectors.joining(", "));
        }
        if (value instanceof Map<?, ?>) {
            try {
                return OBJECT_MAPPER.writeValueAsString(value);
            } catch (Exception ignored) {
                return String.valueOf(value);
            }
        }
        return String.valueOf(value);
    }

    private static String dateText(LocalDateTime value) {
        return value == null ? "" : DATE_TIME_FORMATTER.format(value);
    }

    private static String sanitizeFilename(String filename) {
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private record FormField(String field, String title) {
    }
}
