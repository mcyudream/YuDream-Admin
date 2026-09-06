package online.yudream.base.infra.platform.milky.official;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.enumerate.MilkyConnectionProtocol;
import online.yudream.base.domain.platform.milky.model.MilkyModels.Context;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 官方机器人 access_token 缓存。过期前 60 秒刷新。 */
@Component
@Slf4j
public class OfficialQqBotAccessTokenClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final Duration REFRESH_SKEW = Duration.ofSeconds(60);
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, CachedToken> tokens = new ConcurrentHashMap<>();

    public String token(Context context) {
        if (context == null || blank(context.appId()) || blank(context.appSecret())) {
            throw new BizException("官方机器人 AppID/AppSecret 不能为空");
        }
        String key = context.appId();
        CachedToken cached = tokens.get(key);
        if (cached != null && Instant.now().isBefore(cached.refreshAt())) {
            return cached.accessToken();
        }
        synchronized (key.intern()) {
            cached = tokens.get(key);
            if (cached != null && Instant.now().isBefore(cached.refreshAt())) {
                return cached.accessToken();
            }
            CachedToken issued = fetch(context);
            tokens.put(key, issued);
            return issued.accessToken();
        }
    }

    public String authorization(Context context) {
        return "QQBot " + token(context);
    }

    public void invalidate(String appId) {
        if (!blank(appId)) {
            tokens.remove(appId);
        }
    }

    private CachedToken fetch(Context context) {
        String response;
        try {
            response = WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(HttpClient.create().responseTimeout(TIMEOUT)))
                    .build()
                    .post()
                    .uri(MilkyConnectionProtocol.OFFICIAL_TOKEN_URL)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(Map.of("appId", context.appId(), "clientSecret", context.appSecret()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(TIMEOUT);
        } catch (RuntimeException exception) {
            BizException failure = new BizException("官方机器人 access_token 获取失败");
            failure.initCause(exception);
            throw failure;
        }
        try {
            JsonNode node = mapper.readTree(response == null ? "{}" : response);
            String accessToken = node.path("access_token").asText(null);
            if (blank(accessToken)) {
                throw new BizException("官方机器人 access_token 响应无效");
            }
            int expiresIn = node.path("expires_in").asInt(7200);
            Instant refreshAt = Instant.now().plusSeconds(Math.max(30, expiresIn)).minus(REFRESH_SKEW);
            log.info("Official QQ bot access token refreshed: appId={}", context.appId());
            return new CachedToken(accessToken, refreshAt);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            BizException failure = new BizException("官方机器人 access_token 响应无法解析");
            failure.initCause(exception);
            throw failure;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record CachedToken(String accessToken, Instant refreshAt) { }
}
