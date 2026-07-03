package online.yudream.base.application.system.security.cmd;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class ApiSecurityPolicyUpdateCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private boolean apiEncryptionEnabled;
    private boolean dualTokenEnabled;
    private boolean apiKeyEnabled;
    private boolean passkeyEnabled;
    private boolean oauthServerEnabled;
    private boolean oauthClientEnabled;
    private long accessTokenTtlSeconds;
    private long refreshTokenTtlSeconds;
    private boolean refreshRotationEnabled;
}
