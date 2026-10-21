package online.yudream.base.application.system.template.dto;

import java.util.List;

/** 当前系统可导出的数据模板快照。 */
public record SystemTemplateExportDTO(
        List<TemplateDeptDTO> departments,
        List<TemplateRoleDTO> roles,
        List<TemplateUserDTO> users,
        List<TemplateMenuDTO> menus
) {
}
