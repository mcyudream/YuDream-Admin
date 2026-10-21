package online.yudream.base.application.system.template.dto;

import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;

/** 可移植系统模板中的菜单输出行；插件菜单不进入此模板。 */
public record TemplateMenuDTO(
        String sourceId,
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
