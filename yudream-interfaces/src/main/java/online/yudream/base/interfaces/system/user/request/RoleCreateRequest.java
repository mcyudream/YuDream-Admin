package online.yudream.base.interfaces.system.user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import online.yudream.base.domain.system.user.enumerate.RoleColor;
import online.yudream.base.domain.system.user.enumerate.RoleLevel;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class RoleCreateRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "角色名称不能为空")
    private String name;
    @NotBlank(message = "角色编码不能为空")
    private String code;
    @NotNull(message = "所属部门不能为空")
    private Long deptId;
    private RoleLevel level;
    /** Morphicons 名称（形如 morph:shield），可空。 */
    @Size(max = 64, message = "角色图标名称不能超过 64 个字符")
    private String icon;
    /** 代表色固定键，可空，缺省 info。 */
    @Pattern(regexp = RoleColor.CODES_PATTERN, message = "角色代表色不合法")
    private String color;
    private List<String> permissions = new ArrayList<>();
}
