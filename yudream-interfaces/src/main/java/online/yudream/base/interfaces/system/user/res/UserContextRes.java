package online.yudream.base.interfaces.system.user.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserContextRes {
    private UserDeptRes currentDept;
    private UserRoleRes currentRole;
}
