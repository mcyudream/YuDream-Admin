package online.yudream.base.domain.system.user.aggregate;

import lombok.*;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.user.enumerate.RoleColor;
import online.yudream.base.domain.system.user.enumerate.RoleLevel;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.PermissionID;
import online.yudream.base.domain.system.user.valobj.RoleID;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

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
    private String icon;              // Morphicons 名称（形如 morph:shield），可空
    private RoleColor color;          // 代表色，固定语义枚举，默认 info
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
        r.icon = null;
        r.color = RoleColor.INFO;
        r.status = RoleStatus.ACTIVE;
        return r;
    }

    // 创建普通角色
    public static Role create(String name, String code, DeptID deptId, RoleLevel level) {
        return create(name, code, deptId, level, null, RoleColor.INFO);
    }

    // 创建普通角色（含视觉标识）
    public static Role create(String name, String code, DeptID deptId, RoleLevel level, String icon, RoleColor color) {
        Role r = new Role();
        r.name = name;
        r.code = code;
        r.deptId = deptId;
        r.level = level;
        r.systemRole = false;
        r.applyAppearance(icon, color);
        r.status = RoleStatus.ACTIVE;
        return r;
    }

    public void assignPermission(PermissionID permissionId) {
        if (!permissions.contains(permissionId)) {
            permissions.add(permissionId);
        }
    }

    public void updateBasic(String name, String code, DeptID deptId, RoleLevel level) {
        if (systemRole && !this.code.equals(code)) {
            throw new BizException("系统角色编码不可修改");
        }
        this.name = name;
        this.code = code;
        this.deptId = deptId;
        this.level = level;
    }

    /** 更新角色视觉标识：图标可空，代表色为空时回落默认 info。 */
    public void updateAppearance(String icon, RoleColor color) {
        applyAppearance(icon, color);
    }

    private void applyAppearance(String icon, RoleColor color) {
        this.icon = icon == null || icon.isBlank() ? null : icon.trim();
        this.color = color == null ? RoleColor.INFO : color;
    }

    public void replacePermissions(List<PermissionID> permissions) {
        this.permissions = permissions == null ? new ArrayList<>() : new ArrayList<>(permissions);
    }

    /** 按授权快照执行明确的增量变化；未被点名撤销的历史权限原样保留。 */
    public boolean applyPermissionChanges(List<PermissionID> expectedPermissions,
                                          List<PermissionID> grant, List<PermissionID> revoke) {
        if (systemType == SystemRoleType.SUPER_ADMIN
                || (permissions != null && permissions.stream().anyMatch(permission -> "*".equals(permission.getCode())))) {
            throw new BizException("角色拥有通配权限，不支持单项增量授权，请使用原编辑流程");
        }
        List<PermissionID> current = permissions == null ? List.of() : permissions;
        if (expectedPermissions == null || !Objects.equals(current, expectedPermissions)) {
            throw new BizException("角色授权已变化，请刷新后重试");
        }
        List<PermissionID> grants = grant == null ? List.of() : grant;
        Set<PermissionID> revokes = new HashSet<>(revoke == null ? List.of() : revoke);
        if (grants.stream().anyMatch(revokes::contains)) {
            throw new BizException("同一权限不能同时授予和撤销");
        }
        List<PermissionID> next = new ArrayList<>(current);
        next.removeIf(revokes::contains);
        for (PermissionID permission : grants) {
            if (!next.contains(permission)) {
                next.add(permission);
            }
        }
        if (current.equals(next)) {
            return false;
        }
        permissions = next;
        return true;
    }

    public void activate() {
        this.status = RoleStatus.ACTIVE;
    }

    public void deactivate() {
        if (systemRole) {
            throw new BizException("系统角色不可停用");
        }
        this.status = RoleStatus.DEPRECATED;
    }

    public boolean hasPermission(String permissionCode) {
        return permissions.stream().anyMatch(p -> p.getCode().equals(permissionCode));
    }

}
