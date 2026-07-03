package online.yudream.base.application.system.security.cmd;

import lombok.Data;

@Data
public class LoginTokenRefreshCmd {
    private String refreshToken;
}
