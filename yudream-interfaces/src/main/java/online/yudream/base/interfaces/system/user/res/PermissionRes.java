package online.yudream.base.interfaces.system.user.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.system.user.enumerate.PermissionStatus;

@Data
@Builder
public class PermissionRes {
    private String code;
    private String name;
    private String module;
    private String desc;
    private PermissionStatus status;
}
