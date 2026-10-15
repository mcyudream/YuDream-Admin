package online.yudream.base.interfaces.system.user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import online.yudream.base.domain.valobj.HexColor;

import java.io.Serial;
import java.io.Serializable;

@Data
public class DeptCreateRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "部门名称不能为空")
    private String name;
    private String description;
    private Long leaderId;
    private String phone;
    private Long parentId;
    private Integer sortOrder;
    /** 主色 #rrggbb 小写 hex，可空；为空时服务端按部门名称语义补默认色。 */
    @Pattern(regexp = HexColor.PATTERN, message = "部门主色不合法")
    private String color;
}
