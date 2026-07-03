package online.yudream.base.application.system.security.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class OAuthClientCallbackDTO {
    private String providerCode;
    private String subject;
    private String username;
    private String nickname;
    private String email;
    private String avatar;
    private Map<String, Object> raw;
}
