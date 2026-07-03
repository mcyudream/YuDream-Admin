package online.yudream.base.application.system.security.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OAuthAuthorizationDTO {
    private String code;
    private String state;
    private String redirectUri;
    private String redirectUrl;
}
