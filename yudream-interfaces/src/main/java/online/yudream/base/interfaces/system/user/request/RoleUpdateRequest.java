package online.yudream.base.interfaces.system.user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import online.yudream.base.domain.system.user.enumerate.RoleLevel;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class RoleUpdateRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "角色名称不能为空")
    private String name;
    @NotBlank(message = "角色编码不能为空")
    private String code;
    @NotNull(message = "所属部门不能为空")
    private Long deptId;
    private RoleLevel level;
    private RoleStatus status;
    private List<String> permissions = new ArrayList<>();
}
