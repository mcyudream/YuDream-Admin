package online.yudream.base.infra.system.user.dataobj;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.mapping.Document;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@Document(collation = "sysUser")
public class UserDO extends BaseDO {

    private String username;

    private String nickname;

    private String email;

    private String phone;

    private String qq;

    private String password;

    private boolean emailVerified;
}
