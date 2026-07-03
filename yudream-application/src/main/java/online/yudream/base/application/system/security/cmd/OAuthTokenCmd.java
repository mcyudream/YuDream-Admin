package online.yudream.base.application.system.security.cmd;

import lombok.Data;
import online.yudream.base.domain.system.security.enumerate.OAuthClientAuthMethod;

@Data
public class OAuthTokenCmd {
    private String grantType;
    private String clientId;
    private String clientSecret;
    private OAuthClientAuthMethod authMethod;
    private String code;
    private String redirectUri;
    private String refreshToken;
}
