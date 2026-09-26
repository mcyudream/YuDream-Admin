package online.yudream.base.application.system.backup.support;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * EJSON 轻量取值：从单行 EJSON 文档读取顶层字符串字段（业务键判重用）。
 * 业务键均为字符串字段（code/username/key），解析失败按缺失处理。
 */
public final class BackupEjson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BackupEjson() {
    }

    public static String fieldValue(String ejson, String field) {
        if (ejson == null || ejson.isBlank()) {
            return null;
        }
        try {
            Object value = MAPPER.readValue(ejson, Map.class).get(field);
            return value == null ? null : String.valueOf(value);
        } catch (Exception ignored) {
            return null;
        }
    }
}
