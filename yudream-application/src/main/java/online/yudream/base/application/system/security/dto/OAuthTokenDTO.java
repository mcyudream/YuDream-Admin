package online.yudream.base.application.system.security.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OAuthTokenDTO {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private String scope;
}
