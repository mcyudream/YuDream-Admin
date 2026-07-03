package online.yudream.base.interfaces.system.security.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OAuthAuthorizationRes {
    private String code;
    private String state;
    private String redirectUri;
    private String redirectUrl;
}
