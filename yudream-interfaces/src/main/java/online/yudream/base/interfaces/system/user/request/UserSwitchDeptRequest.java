package online.yudream.base.interfaces.system.user.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserSwitchDeptRequest {

    @NotNull(message = "部门ID不能为空")
    private Long deptId;
}
