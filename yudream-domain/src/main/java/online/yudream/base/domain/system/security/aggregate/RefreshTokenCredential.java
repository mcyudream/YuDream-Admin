package online.yudream.base.domain.system.security.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.enumerate.CredentialStatus;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenCredential extends BaseDomain {

    private String tokenHash;
    private Long userId;
    private LocalDateTime expireTime;
    private CredentialStatus status;
    private LocalDateTime usedTime;

    public static RefreshTokenCredential issue(String tokenHash, Long userId, LocalDateTime expireTime) {
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new BizException("刷新令牌不能为空");
        }
        if (userId == null) {
            throw new BizException("刷新令牌用户不能为空");
        }
        RefreshTokenCredential credential = new RefreshTokenCredential();
        credential.tokenHash = tokenHash;
        credential.userId = userId;
        credential.expireTime = expireTime == null ? LocalDateTime.now().plusDays(7) : expireTime;
        credential.status = CredentialStatus.ACTIVE;
        return credential;
    }

    public boolean activeAt(LocalDateTime time) {
        LocalDateTime now = time == null ? LocalDateTime.now() : time;
        return CredentialStatus.ACTIVE == status && expireTime != null && expireTime.isAfter(now);
    }

    public void markUsed(LocalDateTime time) {
        if (!activeAt(time)) {
            throw new BizException("刷新令牌无效或已过期");
        }
        this.usedTime = time == null ? LocalDateTime.now() : time;
    }

    public void revoke() {
        this.status = CredentialStatus.REVOKED;
    }
}
