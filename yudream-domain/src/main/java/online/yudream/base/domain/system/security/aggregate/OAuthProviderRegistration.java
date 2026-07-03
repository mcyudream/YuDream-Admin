package online.yudream.base.domain.system.security.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.enumerate.OAuthClientAuthMethod;
import online.yudream.base.domain.system.security.enumerate.OAuthRegistrationStatus;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthProviderRegistration extends BaseDomain {

    private String code;
    private String name;
    private String issuerUri;
    private String authorizationUri;
    private String tokenUri;
    private String userInfoUri;
    private String clientId;
    private String clientSecretHash;
    private OAuthClientAuthMethod authMethod;
    private List<String> scopes;
    private String redirectUri;
    private OAuthRegistrationStatus status;

    public static OAuthProviderRegistration create(String code, String name) {
        OAuthProviderRegistration registration = new OAuthProviderRegistration();
        registration.code = required(code, "OAuth 提供商编码不能为空");
        registration.name = required(name, "OAuth 提供商名称不能为空");
        registration.authMethod = OAuthClientAuthMethod.CLIENT_SECRET_BASIC;
        registration.scopes = new ArrayList<>(List.of("openid", "profile", "email"));
        registration.status = OAuthRegistrationStatus.ACTIVE;
        return registration;
    }

    public void update(String name,
                       String issuerUri,
                       String authorizationUri,
                       String tokenUri,
                       String userInfoUri,
                       String clientId,
                       String clientSecretHash,
                       OAuthClientAuthMethod authMethod,
                       List<String> scopes,
                       String redirectUri,
                       OAuthRegistrationStatus status) {
        this.name = required(name, "OAuth 提供商名称不能为空");
        this.issuerUri = issuerUri;
        this.authorizationUri = authorizationUri;
        this.tokenUri = tokenUri;
        this.userInfoUri = userInfoUri;
        this.clientId = clientId;
        if (clientSecretHash != null && !clientSecretHash.isBlank()) {
            this.clientSecretHash = clientSecretHash;
        }
        this.authMethod = authMethod == null ? OAuthClientAuthMethod.CLIENT_SECRET_BASIC : authMethod;
        this.scopes = scopes == null ? new ArrayList<>() : new ArrayList<>(scopes);
        this.redirectUri = redirectUri;
        this.status = status == null ? OAuthRegistrationStatus.ACTIVE : status;
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
}
