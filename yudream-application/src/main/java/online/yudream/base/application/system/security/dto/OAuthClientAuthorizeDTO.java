package online.yudream.base.application.system.security.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OAuthClientAuthorizeDTO {
    private String providerCode;
    private String authorizationUrl;
    private String state;
}
