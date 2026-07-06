package online.yudream.base.plugin.authlib.infrastructure.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

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

    public static List<String> readStringList(String body) {
        try {
            return MAPPER.readValue(body == null || body.isBlank() ? "[]" : body,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("请求 JSON 解析失败：" + e.getOriginalMessage(), e);
        }
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 序列化失败：" + e.getOriginalMessage(), e);
        }
    }
}
