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
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sysApiKeyCredential")
public class ApiKeyCredentialDO extends BaseDO {

    private String name;

    @Indexed(unique = true)
    private String keyPrefix;

    private String secretHash;
    private String maskedValue;
    private Long creatorUserId;
    private List<String> permissions = new ArrayList<>();
    private LocalDateTime expireTime;
    private CredentialStatus status;
    private LocalDateTime lastUsedTime;
}
