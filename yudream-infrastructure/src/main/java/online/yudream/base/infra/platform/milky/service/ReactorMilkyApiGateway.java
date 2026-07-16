package online.yudream.base.infra.platform.milky.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.model.MilkyModels.Context;
import online.yudream.base.domain.platform.milky.service.MilkyApiGateway;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Milky HTTP transport. Every documented API is available as POST /api/{api}. */
@Service
public class ReactorMilkyApiGateway implements MilkyApiGateway {
    private static final Pattern API = Pattern.compile("[a-z][a-z0-9_]{0,127}");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Set<String> NUMERIC_ID_FIELDS = Set.of(
            "peer_id", "user_id", "group_id", "sender_id", "operator_id", "target_user_id",
            "message_seq", "message_id", "start_message_seq", "end_message_seq", "face_id");
    private final ObjectMapper mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Override
    public Object invoke(Context context, String api, Object body) {
        if (api == null || !API.matcher(api.trim()).matches()) throw new BizException("Milky API 名称不合法");
        URI base = base(context);
        String request;
        try {
            request = mapper.writeValueAsString(normalizeIdentifiers(body == null ? Map.of() : body, null));
        } catch (JsonProcessingException exception) {
            throw new BizException("Milky 请求 JSON 无法序列化");
        }
        String response = WebClient.builder().baseUrl(base.toString())
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().responseTimeout(TIMEOUT)))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token(context))
                .build().post().uri("/api/" + api.trim()).contentType(MediaType.APPLICATION_JSON).bodyValue(request)
                .retrieve().onStatus(status -> status.isError(), result -> result.bodyToMono(String.class)
                        .map(text -> new BizException("Milky 服务请求失败（HTTP " + result.statusCode().value() + "）：" + text)))
                .bodyToMono(String.class).block(TIMEOUT);
        try {
            JsonNode envelope = mapper.readTree(response);
            int retcode = envelope.path("retcode").asInt(0);
            if (retcode != 0) throw new BizException("Milky 调用失败：" + envelope.path("message").asText("retcode=" + retcode));
            return mapper.convertValue(envelope.path("data"), Object.class);
        } catch (JsonProcessingException exception) {
            throw new BizException("Milky 响应 JSON 无法解析");
        }
    }

    private URI base(Context context) {
        if (context == null || context.baseUrl() == null) throw new BizException("Milky 地址不能为空");
        try {
            URI uri = URI.create(context.baseUrl());
            if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null) return uri;
        } catch (IllegalArgumentException ignored) { }
        throw new BizException("Milky 地址必须是有效的 HTTP 地址");
    }

    private String token(Context context) {
        if (context.token() == null || context.token().isBlank()) throw new BizException("Milky Token 不能为空");
        return context.token();
    }

    /**
     * API models use JSON numbers for QQ and message identifiers, while the host/plugin/frontend
     * boundaries keep them as strings to avoid JavaScript precision loss. Convert only at egress.
     */
    private Object normalizeIdentifiers(Object value, String fieldName) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> normalized = new java.util.LinkedHashMap<>();
            raw.forEach((key, item) -> {
                String name = String.valueOf(key);
                normalized.put(name, normalizeIdentifiers(item, name));
            });
            return normalized;
        }
        if (value instanceof List<?> values) {
            return values.stream().map(item -> normalizeIdentifiers(item, null)).toList();
        }
        if (fieldName != null && NUMERIC_ID_FIELDS.contains(fieldName) && value instanceof String text && text.matches("\\d+")) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return value;
            }
        }
        return value;
    }
}
