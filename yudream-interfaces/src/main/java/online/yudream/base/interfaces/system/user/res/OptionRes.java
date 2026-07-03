package online.yudream.base.interfaces.system.user.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OptionRes {
    private Long id;
    private String label;
    private String value;
    private Long deptId;
    private String deptName;
}
