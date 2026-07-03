package online.yudream.base.domain.system.security.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.enumerate.OAuthAuthorizationCodeStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthAuthorizationCode extends BaseDomain {

    private String code;
    private String clientId;
    private Long userId;
    private String redirectUri;
    private List<String> scopes;
    private String state;
    private LocalDateTime expireTime;
    private LocalDateTime usedTime;
    private OAuthAuthorizationCodeStatus status;

    public static OAuthAuthorizationCode issue(String code,
                                               String clientId,
                                               Long userId,
                                               String redirectUri,
                                               List<String> scopes,
                                               String state,
                                               LocalDateTime expireTime) {
        if (userId == null) {
            throw new BizException("OAuth 授权用户不能为空");
        }
        OAuthAuthorizationCode authorizationCode = new OAuthAuthorizationCode();
        authorizationCode.code = required(code, "OAuth 授权码不能为空");
        authorizationCode.clientId = required(clientId, "OAuth 客户端ID不能为空");
        authorizationCode.userId = userId;
        authorizationCode.redirectUri = required(redirectUri, "OAuth 回调地址不能为空");
        authorizationCode.scopes = scopes == null ? new ArrayList<>() : new ArrayList<>(scopes);
        authorizationCode.state = state;
        authorizationCode.expireTime = expireTime == null ? LocalDateTime.now().plusMinutes(5) : expireTime;
        authorizationCode.status = OAuthAuthorizationCodeStatus.ACTIVE;
        return authorizationCode;
    }

    public void use(LocalDateTime time) {
        LocalDateTime now = time == null ? LocalDateTime.now() : time;
        if (!activeAt(now)) {
            throw new BizException("OAuth 授权码无效或已过期");
        }
        this.status = OAuthAuthorizationCodeStatus.USED;
        this.usedTime = now;
    }

    public boolean activeAt(LocalDateTime time) {
        LocalDateTime now = time == null ? LocalDateTime.now() : time;
        return OAuthAuthorizationCodeStatus.ACTIVE == status && expireTime != null && expireTime.isAfter(now);
    }

    public void refreshExpiryStatus(LocalDateTime time) {
        if (OAuthAuthorizationCodeStatus.ACTIVE == status && !activeAt(time)) {
            this.status = OAuthAuthorizationCodeStatus.EXPIRED;
        }
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(message);
        }
        return value.trim();
    }
}
