package online.yudream.base.interfaces.system.user.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserRoleRes {
    private Long id;
    private String name;
    private String code;
    private boolean current;
}
