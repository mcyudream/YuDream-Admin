package online.yudream.base.application.system.template.cmd;

import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;

/** 菜单模板行。菜单编码是跨系统身份，sourceId 仅用于同源定位。 */
public record TemplateMenuImportCmd(
        Long sourceId,
        String code,
        String name,
        MenuNodeType type,
        String parentCode,
        String module,
        String icon,
        String path,
        String component,
        String link,
        Integer sort,
        Boolean visible,
        String permission,
        MenuStatus status
) {
}
