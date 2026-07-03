package online.yudream.base.application.system.security.cmd;

import lombok.Data;

@Data
public class OAuthClientCallbackCmd {
    private String providerCode;
    private String code;
    private String state;
}
