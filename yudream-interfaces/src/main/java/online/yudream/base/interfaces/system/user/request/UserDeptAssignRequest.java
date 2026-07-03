package online.yudream.base.interfaces.system.user.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserDeptAssignRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "部门ID不能为空")
    private Long deptId;
    private boolean defaultDept;
}
