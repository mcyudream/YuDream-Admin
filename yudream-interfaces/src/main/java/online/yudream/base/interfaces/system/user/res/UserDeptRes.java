package online.yudream.base.interfaces.system.user.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserDeptRes {
    private Long id;
    private String name;
    private boolean current;
    private boolean defaultDept;
}
