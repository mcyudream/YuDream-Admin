package online.yudream.base.application.system.user.cmd;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserDeptAssignCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long deptId;
    private boolean defaultDept;
}
