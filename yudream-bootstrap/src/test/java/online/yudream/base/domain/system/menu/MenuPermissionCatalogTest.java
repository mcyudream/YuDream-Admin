package online.yudream.base.domain.system.menu;

import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.domain.system.menu.repo.MenuRepo;
import online.yudream.base.domain.system.menu.service.MenuDomainService;
import online.yudream.base.domain.system.user.aggregate.Permission;
import online.yudream.base.domain.system.user.enumerate.PermissionSource;
import online.yudream.base.domain.system.user.enumerate.PermissionStatus;
import online.yudream.base.domain.system.user.repo.PermissionRepo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MenuPermissionCatalogTest {

    @Test
    void rebuildOnlyRegistersLeafPermissionsWithoutSavingMenus() {
        MenuRepo menus = mock(MenuRepo.class);
        PermissionRepo permissions = mock(PermissionRepo.class);
        Menu root = menu("system", "系统管理", MenuNodeType.CATEGORY, null);
        Menu layout = menu("system:personnel", "人员管理", MenuNodeType.LAYOUT, null);
        Menu page = menu("system:user", "用户管理", MenuNodeType.MENU, null);
        int count = new MenuDomainService(menus, permissions).syncPermissionCatalog(List.of(root, layout, page));
        assertEquals(1, count);
        ArgumentCaptor<Permission> saved = ArgumentCaptor.forClass(Permission.class);
        verify(permissions).save(saved.capture());
        assertEquals("system:user", saved.getValue().getId().getCode());
        assertEquals("访问页面入口（system:user）", saved.getValue().getName());
        assertTrue(saved.getValue().getDescription().contains("用户管理"));
        assertEquals("系统管理", saved.getValue().getModule());
        verifyNoInteractions(menus);
    }

    @Test
    void neverOverwritesRealAnnotationOrManualMetadataWithMenuTitles() {
        for (PermissionSource source : List.of(PermissionSource.ANNOTATION, PermissionSource.MANUAL)) {
            PermissionRepo permissions = mock(PermissionRepo.class);
            Permission owned = Permission.create("system:user:view", "查看用户", "系统管理", "查看用户资料", source);
            when(permissions.findByCode("system:user:view")).thenReturn(Optional.of(owned));
            new MenuDomainService(mock(MenuRepo.class), permissions).syncPermissionCatalog(List.of(
                    menu("some-page", "随意改过的菜单名称", MenuNodeType.MENU, "system:user:view")));
            assertEquals("查看用户", owned.getName());
            assertEquals("查看用户资料", owned.getDescription());
            assertEquals(source, owned.getSource());
            verify(permissions, never()).save(any());
        }
    }

    @Test
    void sharedLeafPermissionHasStableMetadataAndStaysActiveWhileAnyReferenceIsActive() {
        Menu page = menu("page", "页面", MenuNodeType.MENU, "resource:item:view");
        page.setStatus(MenuStatus.DISABLED);
        Menu button = menu("button", "查看详情", MenuNodeType.BUTTON, "resource:item:view");
        Permission first = rebuild(List.of(page, button));
        Permission second = rebuild(List.of(button, page));
        assertEquals(first.getName(), second.getName());
        assertEquals(first.getDescription(), second.getDescription());
        assertEquals("查看权限（resource:item:view）", first.getName());
        assertEquals(PermissionStatus.ACTIVE, first.getStatus());
        assertTrue(first.getDescription().contains("页面"));
        assertTrue(first.getDescription().contains("查看详情"));
    }

    @Test
    void planningRequiresNoRepositoryAndPreservesMenuAndRoleConfiguration() {
        Menu page = menu("page", "页面", MenuNodeType.MENU, "resource:item:view");
        var plan = MenuDomainService.planPermissionCatalog(List.of(page));
        assertEquals(1, plan.size());
        assertEquals("resource:item:view", plan.getFirst().getId().getCode());
        assertEquals("页面", page.getName());
        assertEquals("resource:item:view", page.getPermission());
    }

    @Test
    void unavailablePluginMenusDoNotCreateActiveFallbackPermissions() {
        Menu plugin = menu("plugin:missing:page", "暂不可用页面", MenuNodeType.MENU, null);
        plugin.setSource(MenuSource.PLUGIN);
        plugin.setRuntimeAvailable(false);
        assertEquals(PermissionStatus.DEPRECATED, rebuild(List.of(plugin)).getStatus());
    }

    @Test
    void removingAMenuDoesNotRemoveItsRealEndpointPermission() {
        MenuRepo menus = mock(MenuRepo.class);
        PermissionRepo permissions = mock(PermissionRepo.class);
        Menu page = menu("page", "页面", MenuNodeType.MENU, "resource:item:view");
        when(menus.findByCode("page")).thenReturn(Optional.of(page));
        when(permissions.findByCode("resource:item:view")).thenReturn(Optional.of(
                Permission.create("resource:item:view", "查看条目", "业务", "真实说明", PermissionSource.ANNOTATION)));
        new MenuDomainService(menus, permissions).deleteMenu("page");
        verify(menus).deleteByCode("page");
        verify(permissions, never()).deleteByCode(anyString());
    }

    private Permission rebuild(List<Menu> nodes) {
        PermissionRepo permissions = mock(PermissionRepo.class);
        assertEquals(1, new MenuDomainService(mock(MenuRepo.class), permissions).syncPermissionCatalog(nodes));
        ArgumentCaptor<Permission> saved = ArgumentCaptor.forClass(Permission.class);
        verify(permissions).save(saved.capture());
        return saved.getValue();
    }

    private Menu menu(String code, String name, MenuNodeType type, String permission) {
        return Menu.builder().code(code).name(name).type(type).permission(permission).module("system").build();
    }
}
