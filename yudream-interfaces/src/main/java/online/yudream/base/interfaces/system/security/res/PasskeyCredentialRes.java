package online.yudream.base.interfaces.system.security.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.system.security.enumerate.CredentialStatus;

import java.time.LocalDateTime;

@Data
@Builder
public class PasskeyCredentialRes {
    private Long id;
    private Long userId;
    private String credentialId;
    private String deviceName;
    private CredentialStatus status;
    private long signCount;
    private LocalDateTime lastUsedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
