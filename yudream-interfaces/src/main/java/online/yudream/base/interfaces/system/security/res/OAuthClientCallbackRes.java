package online.yudream.base.interfaces.system.security.res;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class OAuthClientCallbackRes {
    private String providerCode;
    private String subject;
    private String username;
    private String nickname;
    private String email;
    private String avatar;
    private Map<String, Object> raw;
}
