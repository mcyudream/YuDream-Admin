package online.yudream.base.interfaces.system.security.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.system.security.enumerate.OAuthClientAuthMethod;
import online.yudream.base.domain.system.security.enumerate.OAuthGrantType;
import online.yudream.base.domain.system.security.enumerate.OAuthRegistrationStatus;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OAuthClientRes {
    private Long id;
    private String clientId;
    private String clientName;
    private OAuthClientAuthMethod authMethod;
    private List<OAuthGrantType> grantTypes;
    private List<String> redirectUris;
    private List<String> scopes;
    private long accessTokenTtlSeconds;
    private long refreshTokenTtlSeconds;
    private OAuthRegistrationStatus status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
