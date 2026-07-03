package online.yudream.base.application.system.user.cmd;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class DeptCreateCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String name;
    private String description;
    private Long leaderId;
    private String phone;
    private Long parentId;
    private Integer sortOrder;
}
