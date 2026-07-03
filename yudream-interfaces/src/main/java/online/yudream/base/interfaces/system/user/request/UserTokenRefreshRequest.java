package online.yudream.base.interfaces.system.user.request;

import lombok.Data;

@Data
public class UserTokenRefreshRequest {
    private String refreshToken;
}
