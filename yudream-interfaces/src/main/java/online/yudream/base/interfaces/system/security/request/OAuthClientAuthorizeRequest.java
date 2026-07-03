package online.yudream.base.interfaces.system.security.request;

import lombok.Data;

@Data
public class OAuthClientAuthorizeRequest {
    private String state;
}
