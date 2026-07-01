package online.yudream.base.domain.system.user.aggregate;

import lombok.*;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.system.user.enumerate.RoleLevel;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.PermissionID;
import online.yudream.base.domain.system.user.valobj.RoleID;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Getter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class Role extends BaseDomain {
    private String name;
    private DeptID deptId;
    private String code;
    private RoleLevel level;          // 角色权重
    private boolean systemRole;
    private SystemRoleType systemType;
    private List<PermissionID> permissions = new ArrayList<>();
    private RoleStatus status;

    // 创建系统角色
    public static Role createSystemRole(SystemRoleType type, DeptID deptId) {
        Role r = new Role();
        r.setId(RoleID.genSystemRoleID(type));
        r.name = type.getName();
        r.code = type.getCode();
        r.deptId = deptId;
        r.level = type.getLevel();
        r.systemRole = true;
        r.systemType = type;
        r.status = RoleStatus.ACTIVE;
        return r;
    }

    // 创建普通角色
    public static Role create(String name, String code, DeptID deptId, RoleLevel level) {
        Role r = new Role();
        r.name = name;
        r.code = code;
        r.deptId = deptId;
        r.level = level;
        r.systemRole = false;
        r.status = RoleStatus.ACTIVE;
        return r;
    }

    public void assignPermission(PermissionID permissionId) {
        if (!permissions.contains(permissionId)) {
            permissions.add(permissionId);
        }
    }

    public boolean hasPermission(String permissionCode) {
        return permissions.stream().anyMatch(p -> p.getCode().equals(permissionCode));
    }

}
