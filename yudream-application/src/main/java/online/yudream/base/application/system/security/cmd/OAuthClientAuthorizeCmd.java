package online.yudream.base.application.system.security.cmd;

import lombok.Data;

@Data
public class OAuthClientAuthorizeCmd {
    private String providerCode;
    private String state;
}
