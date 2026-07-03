package online.yudream.base.interfaces.system.user.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserSwitchRoleRequest {

    @NotNull(message = "角色ID不能为空")
    private Long roleId;
}
