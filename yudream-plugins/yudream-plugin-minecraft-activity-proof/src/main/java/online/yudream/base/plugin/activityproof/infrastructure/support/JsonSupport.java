package online.yudream.base.plugin.activityproof.infrastructure.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonSupport() {
    }

    public static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json == null || json.isBlank() ? "{}" : json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("请求 JSON 解析失败：" + e.getMessage(), e);
        }
    }
}
