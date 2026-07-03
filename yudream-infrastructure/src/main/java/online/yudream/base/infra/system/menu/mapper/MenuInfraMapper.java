package online.yudream.base.infra.system.menu.mapper;

import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.menu.aggregate.Menu;
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
        menuDO.setPermission(menu.getPermission());
        menuDO.setStatus(menu.getStatus());
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
                .permission(menuDO.getPermission())
                .status(menuDO.getStatus())
                .children(Collections.emptyList())
                .build();
    }
}
