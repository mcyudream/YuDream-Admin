package online.yudream.base.domain.system.security.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.enumerate.CredentialStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthAccessToken extends BaseDomain {

    private String accessTokenHash;
    private String refreshTokenHash;
    private String clientId;
    private Long userId;
    private List<String> scopes;
    private LocalDateTime accessExpireTime;
    private LocalDateTime refreshExpireTime;
    private CredentialStatus status;

    public static OAuthAccessToken issue(String accessTokenHash,
                                         String refreshTokenHash,
                                         String clientId,
                                         Long userId,
                                         List<String> scopes,
                                         LocalDateTime accessExpireTime,
                                         LocalDateTime refreshExpireTime) {
        if (userId == null) {
            throw new BizException("OAuth Token 用户不能为空");
        }
        OAuthAccessToken token = new OAuthAccessToken();
        token.accessTokenHash = required(accessTokenHash, "OAuth Access Token 不能为空");
        token.refreshTokenHash = required(refreshTokenHash, "OAuth Refresh Token 不能为空");
        token.clientId = required(clientId, "OAuth 客户端ID不能为空");
        token.userId = userId;
        token.scopes = scopes == null ? new ArrayList<>() : new ArrayList<>(scopes);
        token.accessExpireTime = accessExpireTime;
        token.refreshExpireTime = refreshExpireTime;
        token.status = CredentialStatus.ACTIVE;
        return token;
    }

    public void revoke() {
        this.status = CredentialStatus.REVOKED;
    }

    public boolean refreshActiveAt(LocalDateTime time) {
        LocalDateTime now = time == null ? LocalDateTime.now() : time;
        return CredentialStatus.ACTIVE == status && refreshExpireTime != null && refreshExpireTime.isAfter(now);
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(message);
        }
        return value.trim();
    }
}
