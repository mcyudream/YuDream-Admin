package online.yudream.base.application.system.security.cmd;

import lombok.Data;

@Data
public class OAuthTokenCmd {
    private String grantType;
    private String clientId;
    private String clientSecret;
    private String code;
    private String redirectUri;
    private String refreshToken;
}
