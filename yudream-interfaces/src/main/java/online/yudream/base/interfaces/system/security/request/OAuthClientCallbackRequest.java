package online.yudream.base.interfaces.system.security.request;

import lombok.Data;

@Data
public class OAuthClientCallbackRequest {
    private String code;
    private String state;
}
