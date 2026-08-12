package online.yudream.base.application.system.security.dto;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.application.system.user.dto.UserLoginDTO;

@Data
@Builder
public class ExternalLoginCallbackDTO {
    private Outcome outcome;
    private UserLoginDTO session;
    private String bindingToken;
    private String providerCode;
    private String type;
    private String nickname;
    private String avatarUrl;

    public enum Outcome {
        LOGIN,
        BIND_REQUIRED,
        BOUND
    }
}
