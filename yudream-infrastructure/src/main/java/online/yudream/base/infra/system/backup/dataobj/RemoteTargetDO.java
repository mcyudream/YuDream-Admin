package online.yudream.base.infra.system.backup.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.system.backup.enumerate.RemoteTargetType;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = true)
@Document("sysBackupTarget")
public class RemoteTargetDO extends BaseDO {
    @Indexed(unique = true)
    private String code;
    private String name;
    private RemoteTargetType type;
    private String host;
    private Integer port;
    private String username;
    /** 主密钥加密后的密码密文。 */
    private String password;
    private String basePath;
    private boolean passiveMode;
    private boolean enabled;
}
