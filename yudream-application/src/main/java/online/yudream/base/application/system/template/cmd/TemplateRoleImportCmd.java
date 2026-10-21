package online.yudream.base.application.system.template.cmd;

import online.yudream.base.domain.system.user.enumerate.RoleLevel;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;

import java.util.List;

/** 角色模板行，角色编码是跨系统身份，sourceId 仅用于同源定位。 */
public record TemplateRoleImportCmd(
        Long sourceId,
        String code,
        String name,
        String deptPath,
        RoleLevel level,
        List<String> permissions,
        String icon,
        String color,
        boolean systemRole,
        RoleStatus status
) {
}
