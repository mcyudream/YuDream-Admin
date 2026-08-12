package online.yudream.base.interfaces.system.security.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.application.system.user.dto.UserLoginDTO;

@Data
@Builder
public class ExternalLoginCallbackRes {
    private String outcome;
    private UserLoginDTO session;
    private String bindingToken;
    private String providerCode;
    private String type;
    private String nickname;
    private String avatarUrl;
}
