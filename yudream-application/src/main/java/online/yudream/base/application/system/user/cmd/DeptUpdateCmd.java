package online.yudream.base.application.system.user.cmd;

import lombok.Data;
import online.yudream.base.domain.system.user.enumerate.DeptStatus;

import java.io.Serial;
import java.io.Serializable;

@Data
public class DeptUpdateCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private Long leaderId;
    private String phone;
    private Long parentId;
    private Integer sortOrder;
    private DeptStatus status;
}
