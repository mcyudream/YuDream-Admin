package online.yudream.base.application.system.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.security.enumerate.CredentialStatus;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyCredentialDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String keyPrefix;
    private String maskedValue;
    private Long creatorUserId;
    @Builder.Default
    private List<String> permissions = new ArrayList<>();
    private LocalDateTime expireTime;
    private CredentialStatus status;
    private LocalDateTime lastUsedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
