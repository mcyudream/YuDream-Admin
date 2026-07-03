package online.yudream.base.application.system.user.query;

import lombok.Data;
import online.yudream.base.domain.system.user.enumerate.DeptStatus;

import java.io.Serial;
import java.io.Serializable;

@Data
public class DeptTreeQuery implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String keyword;
    private Long parentId;
    private DeptStatus status;
}
