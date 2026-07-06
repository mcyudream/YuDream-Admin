package online.yudream.base.infra.system.menu.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.SeedSyncMode;
import online.yudream.base.domain.system.menu.service.MenuDomainService;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.valobj.PermissionID;
import online.yudream.base.infra.platform.menu.enumerate.PlatformMenuModule;
import online.yudream.base.infra.system.menu.config.SystemSeedProperties;
import online.yudream.base.infra.system.menu.enumerate.SystemMenuModule;
import online.yudream.base.infra.system.menu.scanner.MenuEnumScanner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统菜单初始化器。
 * <p>
 * 应用启动时解析菜单枚举并同步到数据库，同时将菜单权限赋予超级管理员角色。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class SystemMenuInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final MenuDomainService menuDomainService;
    private final RoleRepo roleRepo;
    private final SystemSeedProperties systemSeedProperties;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        SeedSyncMode syncMode = systemSeedProperties.getMenu().getSyncMode();
        log.info("Initializing system menus from enums, syncMode={}", syncMode);
        List<Class<? extends Enum<?>>> moduleClasses = List.of(SystemMenuModule.class, PlatformMenuModule.class);
        List<Menu> modules = MenuEnumScanner.scan(moduleClasses);
        List<Menu> syncedMenus = menuDomainService.syncMenus(modules, syncMode);

        bindPermissionsToSystemRoles(modules);
        log.info("System menus initialized, modules={}, syncedMenus={}, syncMode={}", modules.size(), syncedMenus.size(), syncMode);
    }

    private void bindPermissionsToSystemRoles(List<Menu> modules) {
        for (SystemRoleType roleType : List.of(SystemRoleType.SUPER_ADMIN, SystemRoleType.ADMIN)) {
            bindPermissionsToRole(roleType, modules);
        }
    }

    private void bindPermissionsToRole(SystemRoleType roleType, List<Menu> modules) {
        Role role = roleRepo.findBySystemType(roleType).orElse(null);
        if (role == null) {
            log.warn("{} role not found, skip menu permission binding", roleType);
            return;
        }
        List<String> permissionCodes = collectPermissionCodes(modules);
        boolean changed = false;
        for (String code : permissionCodes) {
            PermissionID permissionId = PermissionID.of(code);
            if (!role.getPermissions().contains(permissionId)) {
                role.assignPermission(permissionId);
                changed = true;
            }
        }
        if (changed) {
            roleRepo.save(role);
            log.info("Bound {} menu permissions to {}", permissionCodes.size(), roleType);
        }
    }

    private List<String> collectPermissionCodes(List<Menu> modules) {
        List<String> result = new ArrayList<>();
        for (Menu module : modules) {
            collectCodes(module, result);
        }
        return result;
    }

    private void collectCodes(Menu menu, List<String> result) {
        if (menu.getType() == MenuNodeType.CATEGORY
                || menu.getType() == MenuNodeType.LAYOUT
                || menu.getType() == MenuNodeType.MENU
                || menu.getType() == MenuNodeType.LINK
                || menu.getType() == MenuNodeType.BUTTON) {
            result.add(menu.getPermissionCode());
        }
        if (menu.getChildren() != null) {
            for (Menu child : menu.getChildren()) {
                collectCodes(child, result);
            }
        }
    }
}
