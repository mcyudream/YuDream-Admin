package online.yudream.base.interfaces.system.user.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

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
}
