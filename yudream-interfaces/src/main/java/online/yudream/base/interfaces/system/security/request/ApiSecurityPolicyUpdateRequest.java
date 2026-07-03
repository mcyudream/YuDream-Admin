package online.yudream.base.interfaces.system.security.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ApiSecurityPolicyUpdateRequest {

    private boolean apiEncryptionEnabled;
    private boolean dualTokenEnabled;
    private boolean apiKeyEnabled;
    private boolean passkeyEnabled;
    private boolean oauthServerEnabled;
    private boolean oauthClientEnabled;

    @Min(value = 1, message = "访问令牌有效期必须大于0")
    private long accessTokenTtlSeconds;

    @Min(value = 1, message = "刷新令牌有效期必须大于0")
    private long refreshTokenTtlSeconds;

    private boolean refreshRotationEnabled;
}
