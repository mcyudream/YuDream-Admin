package online.yudream.base.domain.system.menu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.domain.system.menu.repo.MenuRepo;
import online.yudream.base.domain.system.user.aggregate.Permission;
import online.yudream.base.domain.system.user.enumerate.PermissionSource;
import online.yudream.base.domain.system.user.enumerate.PermissionStatus;
import online.yudream.base.domain.system.user.repo.PermissionRepo;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 菜单领域服务。
 */
@Slf4j
@RequiredArgsConstructor
public class MenuDomainService {

    private final MenuRepo menuRepo;
    private final PermissionRepo permissionRepo;

    /**
     * 同步菜单树到数据库，并为每个节点维护一条 source = MENU 的权限记录。
     *
     * @param menus 解析后的菜单树
     */
    public void syncMenus(Collection<Menu> menus) {
        if (menus == null || menus.isEmpty()) {
            return;
        }
        List<Menu> flattened = flatten(menus);
        for (Menu menu : flattened) {
            syncMenu(menu);
        }
    }

    public Menu syncMenu(Menu menu) {
        Menu saved = menuRepo.save(menu);
        log.debug("Synced menu: code={}, name={}", saved.getCode(), saved.getName());
        upsertMenuPermission(saved);
        return saved;
    }

    private void upsertMenuPermission(Menu menu) {
        if (menu.getType() == MenuNodeType.CATEGORY || menu.getType() == MenuNodeType.LAYOUT) {
            return;
        }
        String permissionCode = menu.getPermissionCode();
        Permission permission = permissionRepo.findByCode(permissionCode)
                .orElseGet(() -> Permission.create(permissionCode, menu.getName(), menu.getModule(), "菜单权限"));
        permission.setSource(PermissionSource.MENU);
        permission.update(menu.getName(), menu.getModule(), "菜单权限");
        permission.setStatus(menu.getStatus() == MenuStatus.ACTIVE ? PermissionStatus.ACTIVE : PermissionStatus.DEPRECATED);
        permissionRepo.save(permission);
    }

    private List<Menu> flatten(Collection<Menu> menus) {
        return menus.stream()
                .flatMap(this::flattenWithChildren)
                .collect(Collectors.toList());
    }

    private java.util.stream.Stream<Menu> flattenWithChildren(Menu menu) {
        return java.util.stream.Stream.concat(
                java.util.stream.Stream.of(menu),
                menu.getChildren() == null ? java.util.stream.Stream.empty() : flatten(menu.getChildren()).stream()
        );
    }

    /**
     * 查找所有有效的菜单/目录节点（不含按钮）。
     */
    public List<Menu> findActiveMenus() {
        return menuRepo.findByTypeIn(List.of(MenuNodeType.CATEGORY, MenuNodeType.LAYOUT, MenuNodeType.MENU, MenuNodeType.LINK)).stream()
                .filter(m -> m.getStatus() == MenuStatus.ACTIVE)
                .collect(Collectors.toList());
    }

    /**
     * 按 code 构建菜单索引。
     */
    public Map<String, Menu> findAllMap() {
        return findActiveMenus().stream()
                .collect(Collectors.toMap(Menu::getCode, m -> m));
    }
}
