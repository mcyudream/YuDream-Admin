package online.yudream.base.interfaces.system.user.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.system.user.enumerate.RoleLevel;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class RoleManageRes {
    private Long id;
    private String name;
    private Long deptId;
    private String deptName;
    private String code;
    private RoleLevel level;
    private boolean systemRole;
    private SystemRoleType systemType;
    @Builder.Default
    private List<String> permissions = new ArrayList<>();
    private int permissionCount;
    private RoleStatus status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
