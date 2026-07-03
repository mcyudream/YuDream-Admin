package online.yudream.base.interfaces.system.security.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.system.security.enumerate.CredentialStatus;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ApiKeyCredentialRes {
    private Long id;
    private String name;
    private String keyPrefix;
    private String maskedValue;
    private Long creatorUserId;
    private List<String> permissions;
    private LocalDateTime expireTime;
    private CredentialStatus status;
    private LocalDateTime lastUsedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
