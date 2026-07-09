package online.yudream.base.infra.system.menu.mapper;

import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;
import online.yudream.base.infra.system.menu.dataobj.MenuDO;

import java.util.Collections;

/**
 * 菜单领域对象与数据对象转换器。
 */
@NoArgsConstructor
public class MenuInfraMapper {

    public static MenuDO toDataObj(Menu menu) {
        if (menu == null) return null;
        MenuDO menuDO = new MenuDO();
        menuDO.setCode(menu.getCode());
        menuDO.setName(menu.getName());
        menuDO.setType(menu.getType());
        menuDO.setParentCode(menu.getParentCode());
        menuDO.setModule(menu.getModule());
        menuDO.setIcon(menu.getIcon());
        menuDO.setPath(menu.getPath());
        menuDO.setComponent(menu.getComponent());
        menuDO.setLink(menu.getLink());
        menuDO.setSort(menu.getSort());
        menuDO.setVisible(menu.getVisible());
        menuDO.setPermission(menu.getPermission());
        menuDO.setStatus(menu.getStatus());
        menuDO.setSource(menu.getSource());
        menuDO.setPluginCode(menu.getPluginCode());
        menuDO.setPluginModuleName(menu.getPluginModuleName());
        menuDO.setPluginRegistrationKey(menu.getPluginRegistrationKey());
        menuDO.setRuntimeAvailable(menu.getRuntimeAvailable());
        return menuDO;
    }

    public static Menu toDomain(MenuDO menuDO) {
        if (menuDO == null) return null;
        return Menu.builder()
                .code(menuDO.getCode())
                .name(menuDO.getName())
                .type(menuDO.getType())
                .parentCode(menuDO.getParentCode())
                .module(menuDO.getModule())
                .icon(menuDO.getIcon())
                .path(menuDO.getPath())
                .component(menuDO.getComponent())
                .link(menuDO.getLink())
                .sort(menuDO.getSort())
                .visible(menuDO.getVisible())
                .permission(menuDO.getPermission())
                .status(menuDO.getStatus())
                .source(menuDO.getSource() == null ? MenuSource.SYSTEM : menuDO.getSource())
                .pluginCode(menuDO.getPluginCode())
                .pluginModuleName(menuDO.getPluginModuleName())
                .pluginRegistrationKey(menuDO.getPluginRegistrationKey())
                .runtimeAvailable(menuDO.getRuntimeAvailable())
                .children(Collections.emptyList())
                .build();
    }
}
