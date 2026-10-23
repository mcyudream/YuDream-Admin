package online.yudream.base.infra.system.security.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sysApiSecurityPolicy")
public class ApiSecurityPolicyDO extends BaseDO {

    @Indexed(unique = true)
    private String code;
    private boolean apiEncryptionEnabled;
    private boolean dualTokenEnabled;
    private boolean apiKeyEnabled;
    private boolean passkeyEnabled;
    private boolean oauthServerEnabled;
    private boolean oauthClientEnabled;
    /** 包装类型：存量文档缺字段时为 null，加载侧按 true（开放注册）处理。 */
    private Boolean registrationEnabled;
    private long accessTokenTtlSeconds;
    private long refreshTokenTtlSeconds;
    private boolean refreshRotationEnabled;
}
