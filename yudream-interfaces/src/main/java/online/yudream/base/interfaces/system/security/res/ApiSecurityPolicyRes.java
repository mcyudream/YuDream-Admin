package online.yudream.base.interfaces.system.security.res;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApiSecurityPolicyRes {
    private Long id;
    private boolean apiEncryptionEnabled;
    private boolean dualTokenEnabled;
    private boolean apiKeyEnabled;
    private boolean passkeyEnabled;
    private boolean oauthServerEnabled;
    private boolean oauthClientEnabled;
    private long accessTokenTtlSeconds;
    private long refreshTokenTtlSeconds;
    private boolean refreshRotationEnabled;
    private LocalDateTime updateTime;
}
