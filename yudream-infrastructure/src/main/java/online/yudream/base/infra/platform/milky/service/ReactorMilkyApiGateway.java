package online.yudream.base.infra.platform.milky.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.model.MilkyModels.Context;
import online.yudream.base.domain.platform.milky.service.MilkyApiGateway;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Milky HTTP transport. Every documented API is available as POST /api/{api}. */
@Service
@Slf4j
public class ReactorMilkyApiGateway implements MilkyApiGateway {
    private static final Pattern API = Pattern.compile("[a-z][a-z0-9_]{0,127}");
    private static final Duration TIMEOUT = Duration.ofMinutes(10);
    private static final Set<String> NUMERIC_ID_FIELDS = Set.of(
            "peer_id", "user_id", "group_id", "sender_id", "operator_id", "target_user_id",
            "message_seq", "message_id", "start_message_seq", "end_message_seq", "face_id");
    private final ObjectMapper mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Override
    public Object invoke(Context context, String api, Object body) {
        if (api == null || !API.matcher(api.trim()).matches()) throw new BizException("Milky API 名称不合法");
        String method = api.trim();
        URI base = base(context);
        long startedAt = System.nanoTime();
        String request;
        try {
            request = mapper.writeValueAsString(normalizeIdentifiers(body == null ? Map.of() : body, null));
        } catch (JsonProcessingException exception) {
            throw failure("Milky 请求 JSON 无法序列化", method, base, null, startedAt, exception);
        }
        String response;
        try {
            response = WebClient.builder().baseUrl(base.toString())
                    .clientConnector(new ReactorClientHttpConnector(HttpClient.create().responseTimeout(TIMEOUT)))
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token(context))
                    .build().post().uri("/api/" + method).contentType(MediaType.APPLICATION_JSON).bodyValue(request)
                    .retrieve().onStatus(status -> status.isError(), result -> result.releaseBody()
                            .then(Mono.error(new MilkyHttpException(result.statusCode().value()))))
                    .bodyToMono(String.class).block(TIMEOUT);
        } catch (MilkyHttpException exception) {
            throw failure("Milky 服务请求失败（HTTP " + exception.status() + "）", method, base, exception.status(), startedAt, exception);
        } catch (RuntimeException exception) {
            throw failure("Milky 服务请求失败", method, base, null, startedAt, exception);
        }
        try {
            JsonNode envelope = mapper.readTree(response);
            int retcode = envelope.path("retcode").asInt(0);
            if (retcode != 0) {
                throw failure("Milky 调用失败（retcode=" + retcode + "）", method, base, null, startedAt, null);
            }
            return mapper.convertValue(envelope.path("data"), Object.class);
        } catch (JsonProcessingException exception) {
            throw failure("Milky 响应 JSON 无法解析", method, base, null, startedAt, exception);
        }
    }

    private BizException failure(String message, String api, URI base, Integer status, long startedAt, Throwable cause) {
        log.error("Milky API request failed: api={}, host={}, port={}, status={}, elapsedMs={}", api, base.getHost(),
                base.getPort(), status, Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), cause);
        BizException exception = new BizException(message);
        if (cause != null) exception.initCause(cause);
        return exception;
    }

    private static final class MilkyHttpException extends RuntimeException {
        private final int status;

        private MilkyHttpException(int status) {
            this.status = status;
        }

        private int status() {
            return status;
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
