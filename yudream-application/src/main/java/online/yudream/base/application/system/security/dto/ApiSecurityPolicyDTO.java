package online.yudream.base.application.system.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiSecurityPolicyDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

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
