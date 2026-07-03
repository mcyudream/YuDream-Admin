package online.yudream.base.domain.system.security.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.enumerate.OAuthClientAuthMethod;
import online.yudream.base.domain.system.security.enumerate.OAuthGrantType;
import online.yudream.base.domain.system.security.enumerate.OAuthRegistrationStatus;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthClientRegistration extends BaseDomain {

    private String clientId;
    private String clientName;
    private String clientSecretHash;
    private OAuthClientAuthMethod authMethod;
    private List<OAuthGrantType> grantTypes;
    private List<String> redirectUris;
    private List<String> scopes;
    private long accessTokenTtlSeconds;
    private long refreshTokenTtlSeconds;
    private OAuthRegistrationStatus status;

    public static OAuthClientRegistration create(String clientId, String clientName, String clientSecretHash) {
        OAuthClientRegistration registration = new OAuthClientRegistration();
        registration.clientId = required(clientId, "OAuth 客户端ID不能为空");
        registration.clientName = required(clientName, "OAuth 客户端名称不能为空");
        registration.clientSecretHash = clientSecretHash;
        registration.authMethod = OAuthClientAuthMethod.CLIENT_SECRET_BASIC;
        registration.grantTypes = new ArrayList<>(List.of(OAuthGrantType.AUTHORIZATION_CODE, OAuthGrantType.REFRESH_TOKEN));
        registration.redirectUris = new ArrayList<>();
        registration.scopes = new ArrayList<>(List.of("openid", "profile"));
        registration.accessTokenTtlSeconds = 7200;
        registration.refreshTokenTtlSeconds = 604800;
        registration.status = OAuthRegistrationStatus.ACTIVE;
        return registration;
    }

    public void update(String clientName,
                       OAuthClientAuthMethod authMethod,
                       List<OAuthGrantType> grantTypes,
                       List<String> redirectUris,
                       List<String> scopes,
                       long accessTokenTtlSeconds,
                       long refreshTokenTtlSeconds,
                       OAuthRegistrationStatus status) {
        this.clientName = required(clientName, "OAuth 客户端名称不能为空");
        this.authMethod = authMethod == null ? OAuthClientAuthMethod.CLIENT_SECRET_BASIC : authMethod;
        this.grantTypes = normalizeList(grantTypes, "OAuth 授权模式不能为空");
        this.redirectUris = normalizeList(redirectUris, "OAuth 回调地址不能为空");
        this.scopes = normalizeList(scopes, "OAuth 授权范围不能为空");
        if (accessTokenTtlSeconds <= 0 || refreshTokenTtlSeconds <= 0) {
            throw new BizException("OAuth Token 有效期必须大于0");
        }
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
        this.status = status == null ? OAuthRegistrationStatus.ACTIVE : status;
    }

    public void updateSecret(String clientSecretHash) {
        this.clientSecretHash = clientSecretHash;
    }

    public void disable() {
        this.status = OAuthRegistrationStatus.DISABLED;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private static <T> List<T> normalizeList(List<T> values, String message) {
        if (values == null || values.isEmpty()) {
            throw new BizException(message);
        }
        return new ArrayList<>(values);
    }
}
