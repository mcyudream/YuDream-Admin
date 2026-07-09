package online.yudream.base.plugin.projectprogress.infrastructure.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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
