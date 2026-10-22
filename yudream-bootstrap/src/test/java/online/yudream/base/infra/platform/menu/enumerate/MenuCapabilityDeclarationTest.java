package online.yudream.base.infra.platform.menu.enumerate;

import online.yudream.base.application.system.menu.service.MenuAppService;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.repo.MenuRepo;
import online.yudream.base.domain.system.menu.service.MenuCapabilityRegistry;
import online.yudream.base.domain.system.menu.service.MenuDomainService;
import online.yudream.base.infra.system.menu.enumerate.SystemMenuModules;
import online.yudream.base.infra.system.menu.scanner.MenuEnumScanner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 菜单注解声明的能力归属：编码无法推断出能力的入口（网站工作台、技能库）
 * 在能力关闭时不进入运行期菜单，其所在分组也随之无可见子节点。
 */
class MenuCapabilityDeclarationTest {

    private static final List<Menu> ROOTS = MenuEnumScanner.scan(SystemMenuModules.CLASSES);

    @Test
    void indexesDeclaredCapabilities() {
        Map<String, String> index = MenuEnumScanner.capabilityIndex(SystemMenuModules.CLASSES);
        assertEquals("cms", index.get("platform:theme-center"));
        assertFalse(index.containsKey("platform:cms-workspace"));
        assertEquals("agent", index.get("platform:agent-skill"));
    }

    @Test
    void declaredCapabilityHidesMenusAndButtonsWhenDisabled() {
        CapabilityModuleRepo capabilities = mock(CapabilityModuleRepo.class);
        Map<String, Boolean> enabled = new java.util.HashMap<>();
        when(capabilities.findByCode(anyString())).thenAnswer(call -> Optional.ofNullable(enabled.get(call.<String>getArgument(0)))
                .map(on -> CapabilityModule.builder().code(call.getArgument(0)).enabled(on).build()));
        MenuAppService service = new MenuAppService(mock(MenuDomainService.class), mock(MenuRepo.class), capabilities);
        service.menuCapabilities(new MenuCapabilityRegistry(MenuEnumScanner.capabilityIndex(SystemMenuModules.CLASSES)));
        List<Menu> menus = flatten();

        enabled.put("cms", false);
        enabled.put("agent", false);
        Set<String> off = service.runtimeAvailableMenuCodes(menus);
        assertFalse(off.contains("platform:theme-center"));
        assertFalse(off.contains("platform:cms-workspace"));
        assertFalse(off.contains("platform:agent-skill"));
        assertTrue(menus.stream().filter(menu -> "platform:theme-center".equals(menu.getParentCode())).noneMatch(menu -> off.contains(menu.getCode())),
                "按钮节点随菜单的能力归属一并隐藏");

        enabled.put("cms", true);
        enabled.put("agent", true);
        Set<String> on = service.runtimeAvailableMenuCodes(menus);
        assertTrue(on.contains("platform:theme-center"));
        assertFalse(on.contains("platform:cms-workspace"));
        assertTrue(on.contains("platform:agent-skill"));
    }

    @Test
    void persistedRetiredWorkspaceStaysHiddenEvenWithCapabilitiesEnabled() {
        CapabilityModuleRepo capabilities = mock(CapabilityModuleRepo.class);
        when(capabilities.findByCode(anyString())).thenReturn(Optional.of(CapabilityModule.builder().enabled(true).build()));
        MenuAppService service = new MenuAppService(mock(MenuDomainService.class), mock(MenuRepo.class), capabilities);
        List<Menu> menus = new ArrayList<>(flatten());
        menus.add(Menu.builder().code("platform:cms-workspace").path("/platform/cms/workspace")
                .type(online.yudream.base.domain.system.menu.enumerate.MenuNodeType.MENU).build());
        menus.add(Menu.builder().code("platform:cms-workspace:view").parentCode("platform:cms-workspace")
                .type(online.yudream.base.domain.system.menu.enumerate.MenuNodeType.BUTTON).build());
        Set<String> available = service.runtimeAvailableMenuCodes(menus);
        assertFalse(available.contains("platform:cms-workspace"));
        assertFalse(available.contains("platform:cms-workspace:view"));
        assertTrue(available.contains("platform:theme-center"));
    }

    private static List<Menu> flatten() {
        List<Menu> all = new ArrayList<>();
        ROOTS.forEach(root -> collect(root, all));
        return all;
    }

    private static void collect(Menu menu, List<Menu> all) {
        all.add(menu);
        if (menu.getChildren() != null) {
            menu.getChildren().forEach(child -> collect(child, all));
        }
    }
}
