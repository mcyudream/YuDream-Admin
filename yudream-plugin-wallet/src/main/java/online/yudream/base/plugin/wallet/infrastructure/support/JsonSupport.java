package online.yudream.base.plugin.wallet.infrastructure.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class JsonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonSupport() {
    }

    public static <T> T read(String body, Class<T> type) {
        try {
            return MAPPER.readValue(body == null || body.isBlank() ? "{}" : body, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("请求 JSON 解析失败：" + e.getOriginalMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> readMap(String body) {
        return read(body, Map.class);
    }
}
