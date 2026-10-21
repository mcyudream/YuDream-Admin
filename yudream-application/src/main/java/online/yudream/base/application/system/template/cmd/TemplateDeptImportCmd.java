package online.yudream.base.application.system.template.cmd;

import online.yudream.base.domain.system.user.enumerate.DeptStatus;

/** 部门模板行，路径是跨系统身份，sourceId 仅用于同源定位。 */
public record TemplateDeptImportCmd(
        Long sourceId,
        String path,
        String name,
        String parentPath,
        String leaderUsername,
        String phone,
        Integer sortOrder,
        String description,
        String color,
        boolean systemDept,
        DeptStatus status
) {
}
