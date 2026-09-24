package online.yudream.base.interfaces.system.user.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserRoleRes {
    private Long id;
    private String name;
    private String code;

    /** 角色所属部门，角色按部门归属。 */
    private Long deptId;

    private boolean current;
}
