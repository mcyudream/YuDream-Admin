package online.yudream.base.application.system.security.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginTokenDTO {
    private String token;
    private String tokenName;
    private String refreshToken;
    private boolean dualTokenEnabled;
    private long expiresIn;
}
