package online.yudream.base.domain.platform.plugin.valobj;

import lombok.Builder;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;

@Builder
public record PluginMenuOverrideInfo(
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
    public PluginMenuOverrideInfo {
        visible = visible == null || visible;
        status = status == null ? MenuStatus.ACTIVE : status;
    }

    public PluginMenuOverrideInfo withSort(Integer overriddenSort) {
        return new PluginMenuOverrideInfo(
                code, name, type, parentCode, module, icon, path, component, link,
                overriddenSort, visible, permission, status
        );
    }
}
