package online.yudream.base.application.system.template.cmd;

import java.util.List;

/** 版本化系统数据模板包的应用层输入。 */
public record SystemTemplateImportCmd(
        String format,
        int schemaVersion,
        List<TemplateDeptImportCmd> departments,
        List<TemplateRoleImportCmd> roles,
        List<TemplateUserImportCmd> users,
        List<TemplateMenuImportCmd> menus
) {
}
