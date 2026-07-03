package online.yudream.base.interfaces.system.security.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.system.security.enumerate.OAuthClientAuthMethod;
import online.yudream.base.domain.system.security.enumerate.OAuthRegistrationStatus;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OAuthProviderRes {
    private Long id;
    private String code;
    private String name;
    private String issuerUri;
    private String authorizationUri;
    private String tokenUri;
    private String userInfoUri;
    private String clientId;
    private OAuthClientAuthMethod authMethod;
    private List<String> scopes;
    private String redirectUri;
    private OAuthRegistrationStatus status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
