package online.yudream.base.application.system.template.dto;

import online.yudream.base.domain.system.user.enumerate.RoleLevel;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;

import java.util.List;

/** 可移植系统模板中的角色输出行。 */
public record TemplateRoleDTO(
        String sourceId,
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
