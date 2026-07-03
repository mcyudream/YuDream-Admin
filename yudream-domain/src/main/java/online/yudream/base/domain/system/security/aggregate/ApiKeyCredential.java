package online.yudream.base.domain.system.security.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.enumerate.CredentialStatus;
import online.yudream.base.domain.system.security.valobj.ApiKeySecret;
import online.yudream.base.domain.system.security.valobj.PermissionScope;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyCredential extends BaseDomain {

    private String name;
    private ApiKeySecret secret;
    private Long creatorUserId;
    private PermissionScope permissionScope;
    private LocalDateTime expireTime;
    private CredentialStatus status;
    private LocalDateTime lastUsedTime;

    public static ApiKeyCredential create(String name,
                                          ApiKeySecret secret,
                                          Long creatorUserId,
                                          PermissionScope permissionScope,
                                          LocalDateTime expireTime) {
        if (name == null || name.isBlank()) {
            throw new BizException("API Key 名称不能为空");
        }
        if (secret == null) {
            throw new BizException("API Key 密钥不能为空");
        }
        if (creatorUserId == null) {
            throw new BizException("API Key 创建人不能为空");
        }
        if (permissionScope == null) {
            throw new BizException("API Key 权限范围不能为空");
        }
        return ApiKeyCredential.builder()
                .name(name.trim())
                .secret(secret)
                .creatorUserId(creatorUserId)
                .permissionScope(permissionScope)
                .expireTime(expireTime)
                .status(CredentialStatus.ACTIVE)
                .build();
    }

    public void revoke() {
        this.status = CredentialStatus.REVOKED;
    }

    public void markUsed(LocalDateTime usedTime) {
        if (!activeAt(usedTime)) {
            throw new BizException("API Key 不可用");
        }
        this.lastUsedTime = usedTime == null ? LocalDateTime.now() : usedTime;
    }

    public boolean expiredAt(LocalDateTime time) {
        return expireTime != null && !expireTime.isAfter(time == null ? LocalDateTime.now() : time);
    }

    public boolean activeAt(LocalDateTime time) {
        return CredentialStatus.ACTIVE.equals(status) && !expiredAt(time);
    }

    public void refreshExpiryStatus(LocalDateTime time) {
        if (CredentialStatus.ACTIVE.equals(status) && expiredAt(time)) {
            this.status = CredentialStatus.EXPIRED;
        }
    }
}
