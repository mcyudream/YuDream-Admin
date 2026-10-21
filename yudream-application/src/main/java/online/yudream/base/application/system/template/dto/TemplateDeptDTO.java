package online.yudream.base.application.system.template.dto;

import online.yudream.base.domain.system.user.enumerate.DeptStatus;
import online.yudream.base.domain.system.user.enumerate.SystemDeptType;

/** 可移植系统模板中的部门输出行。 */
public record TemplateDeptDTO(
        String sourceId,
        String path,
        String name,
        String parentPath,
        String leaderUsername,
        String phone,
        Integer sortOrder,
        String description,
        String color,
        SystemDeptType deptType,
        boolean systemDept,
        DeptStatus status
) {
}
