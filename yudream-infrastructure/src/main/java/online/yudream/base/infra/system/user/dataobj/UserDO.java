package online.yudream.base.infra.system.user.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.infra.common.baseobj.BaseDO;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sysUser")
public class UserDO extends BaseDO {

    private String username;

    private String nickname;

    private String email;

    private String phone;

    private String qq;

    private String password;

    private Long avatarFileId;

    private boolean emailVerified;

    private UserStatus status = UserStatus.ACTIVE;

    private List<UserDeptDO> depts = new ArrayList<>();

    private List<Long> roleIds = new ArrayList<>();
}
