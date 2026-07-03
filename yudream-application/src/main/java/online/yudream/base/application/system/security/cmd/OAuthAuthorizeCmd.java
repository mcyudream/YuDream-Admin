package online.yudream.base.application.system.security.cmd;

import lombok.Data;

@Data
public class OAuthAuthorizeCmd {
    private String responseType;
    private String clientId;
    private String redirectUri;
    private String scope;
    private String state;
    private Long userId;
}
