package online.yudream.base.infra.system.security.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.aggregate.OAuthProviderRegistration;
import online.yudream.base.domain.system.security.service.OAuthClientGateway;
import online.yudream.base.domain.system.security.valobj.OAuthClientToken;
import online.yudream.base.domain.system.security.valobj.OAuthClientUserInfo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class JdkOAuthClientGateway implements OAuthClientGateway {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    @Override
    public OAuthClientToken exchangeCode(OAuthProviderRegistration provider, String code, String state) {
        if (!StringUtils.hasText(provider.getTokenUri())) {
            throw new BizException("OAuth 提供商 Token 地址不能为空");
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", provider.getRedirectUri());
        form.put("client_id", provider.getClientId());
        form.put("client_secret", provider.getClientSecretHash());
        Map<String, Object> body = postForm(provider.getTokenUri(), form);
        String accessToken = text(body.get("access_token"));
        if (!StringUtils.hasText(accessToken)) {
            throw new BizException("OAuth 提供商未返回 access_token");
        }
        return new OAuthClientToken(
                accessToken,
                text(body.get("refresh_token")),
                defaultText(body.get("token_type"), "Bearer"),
                longValue(body.get("expires_in")),
                text(body.get("scope"))
        );
    }

    @Override
    public OAuthClientUserInfo userInfo(OAuthProviderRegistration provider, OAuthClientToken token) {
        if (!StringUtils.hasText(provider.getUserInfoUri())) {
            throw new BizException("OAuth 提供商用户信息地址不能为空");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(provider.getUserInfoUri()))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", token.tokenType() + " " + token.accessToken())
                .header("Accept", "application/json")
                .GET()
                .build();
        Map<String, Object> body = send(request);
        return new OAuthClientUserInfo(
                firstText(body, "sub", "id", "openid", "user_id"),
                firstText(body, "preferred_username", "login", "username", "name"),
                firstText(body, "nickname", "name", "display_name"),
                firstText(body, "email"),
                firstText(body, "picture", "avatar_url", "avatar"),
                body
        );
    }

    private Map<String, Object> postForm(String uri, Map<String, String> form) {
        String body = form.entrySet().stream()
                .filter(entry -> StringUtils.hasText(entry.getValue()))
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return send(request);
    }

    private Map<String, Object> send(HttpRequest request) {
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException("OAuth 提供商调用失败：" + response.statusCode());
            }
            return objectMapper.readValue(response.body(), MAP_TYPE);
        } catch (IOException e) {
            throw new BizException("OAuth 提供商响应解析失败");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("OAuth 提供商调用被中断");
        }
    }

    private String firstText(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            String value = text(body.get(key));
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String defaultText(Object value, String defaultValue) {
        String text = text(value);
        return StringUtils.hasText(text) ? text : defaultValue;
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            return Long.parseLong(String.valueOf(value));
        }
        return 0;
    }

    private String encode(String value) {
        return URLEncoder.encode(Objects.toString(value, ""), StandardCharsets.UTF_8);
    }
}
