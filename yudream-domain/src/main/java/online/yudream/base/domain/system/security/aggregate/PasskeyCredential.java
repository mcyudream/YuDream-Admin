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
public class PasskeyCredential extends BaseDomain {

    private Long userId;
    private String credentialId;
    private String publicKey;
    private long signCount;
    private String deviceName;
    private CredentialStatus status;
    private LocalDateTime lastUsedTime;

    public static PasskeyCredential create(Long userId, String credentialId, String publicKey, String deviceName) {
        if (userId == null) {
            throw new BizException("Passkey 用户不能为空");
        }
        PasskeyCredential credential = new PasskeyCredential();
        credential.userId = userId;
        credential.credentialId = required(credentialId, "Passkey 凭据ID不能为空");
        credential.publicKey = required(publicKey, "Passkey 公钥不能为空");
        credential.deviceName = deviceName == null || deviceName.isBlank() ? "未命名设备" : deviceName.trim();
        credential.status = CredentialStatus.ACTIVE;
        return credential;
    }

    public void markUsed(long signCount, LocalDateTime usedTime) {
        this.signCount = Math.max(this.signCount, signCount);
        this.lastUsedTime = usedTime == null ? LocalDateTime.now() : usedTime;
    }

    public void revoke() {
        this.status = CredentialStatus.REVOKED;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(message);
        }
        return value.trim();
    }
}
