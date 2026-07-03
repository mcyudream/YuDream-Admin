package online.yudream.base.application.system.user.cmd;

import lombok.Data;
import online.yudream.base.domain.system.user.enumerate.RoleLevel;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class RoleUpdateCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String code;
    private Long deptId;
    private RoleLevel level;
    private RoleStatus status;
    private List<String> permissions = new ArrayList<>();
}
