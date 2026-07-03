package online.yudream.base.application.system.user.cmd;

import lombok.Data;
import online.yudream.base.domain.system.user.enumerate.RoleLevel;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class RoleCreateCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String name;
    private String code;
    private Long deptId;
    private RoleLevel level;
    private List<String> permissions = new ArrayList<>();
}
