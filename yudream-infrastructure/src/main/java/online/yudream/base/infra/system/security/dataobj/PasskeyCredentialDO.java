package online.yudream.base.infra.system.security.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.security.enumerate.CredentialStatus;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sysPasskeyCredential")
public class PasskeyCredentialDO extends BaseDO {

    private Long userId;

    @Indexed(unique = true)
    private String credentialId;

    private String publicKey;
    private long signCount;
    private String deviceName;
    private CredentialStatus status;
    private LocalDateTime lastUsedTime;
}
