package online.yudream.base.application.system.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.security.enumerate.CredentialStatus;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasskeyCredentialDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

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
