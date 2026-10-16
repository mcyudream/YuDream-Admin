package online.yudream.base.infra.platform.menu.enumerate;

import online.yudream.base.application.system.menu.service.MenuAppService;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.service.MenuDomainService;
import online.yudream.base.domain.system.menu.repo.MenuRepo;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.infra.system.menu.scanner.MenuEnumScanner;
import online.yudream.base.interfaces.platform.feishu.controller.FeishuCloudDocController;
import online.yudream.base.interfaces.platform.feishu.controller.FeishuQuotaController;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 飞书菜单种子与权限：CONNECTION_HUB 下的 MENU 入口、view/edit/config 按钮叶子，
 * 以及「菜单声明码 == 控制器注解注册码」的等价性（同码共享叶子，注解元数据优先，菜单只补缺）。
 */
class PlatformMenuFeishuSeedTest {

    private static final String MENU_CODE = "platform:feishu";
    private static final String VIEW_CODE = "platform:feishu:view";
    private static final String EDIT_CODE = "platform:feishu:edit";
    private static final String CONFIG_CODE = "platform:feishu:config";

    private static final List<Menu> MODULE_ROOTS = MenuEnumScanner.scan(List.of(PlatformMenuModule.class));

    @Test
    void declaresFeishuMenuUnderConnectionHub() {
        Menu feishu = node(MENU_CODE);
        assertEquals("飞书", feishu.getName());
        assertEquals(MenuNodeType.MENU, feishu.getType());
        assertEquals("platform:connection-hub", feishu.getParentCode());
        assertEquals("/platform/feishu", feishu.getPath());
        assertEquals("platform/feishu/index.vue", feishu.getComponent());
        assertEquals("i-ri:cloud-line", feishu.getIcon());
        assertEquals(75, feishu.getSort());
        assertTrue(feishu.isVisibleInMenu());
        assertEquals("platform", feishu.getModule());
    }

    @Test
    void menuEntryIsGatedByTheViewPermission() {
        Menu feishu = node(MENU_CODE);
        // 菜单可见性走 getPermissionCode()：显式挂查看权限，而不是让菜单码自身成为权限码
        assertEquals(VIEW_CODE, feishu.getPermissionCode());
        assertTrue(feishu.getPermissionCode().endsWith(":view"));
    }

    @Test
    void registersViewEditConfigButtonPermissionsUnderTheMenu() {
        Set<String> buttonCodes = MODULE_ROOTS.stream()
                .flatMap(root -> flatten(root).stream())
                .filter(menu -> menu.getType() == MenuNodeType.BUTTON)
                .filter(menu -> menu.getCode().startsWith(MENU_CODE + ":"))
                .map(Menu::getCode)
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(Set.of(CONFIG_CODE, EDIT_CODE, VIEW_CODE), buttonCodes);

        Set<String> parents = flatten(node(MENU_CODE)).stream()
                .filter(menu -> menu.getType() == MenuNodeType.BUTTON)
                .map(Menu::getParentCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(List.of(MENU_CODE), new ArrayList<>(parents));
    }

    @Test
    void plannedPermissionCatalogMatchesControllerAnnotationsExactly() {
        Set<String> annotated = controllerPermissionCodes();
        assertEquals(Set.of(VIEW_CODE, EDIT_CODE, CONFIG_CODE), annotated);

        List<Menu> feishuNodes = flatten(node(MENU_CODE));
        Set<String> declared = feishuNodes.stream()
                .filter(menu -> menu.getType() == MenuNodeType.MENU || menu.getType() == MenuNodeType.BUTTON)
                .map(Menu::getPermissionCode)
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(annotated, declared);

        // 计划出的权限目录只含三个飞书权限码：菜单入口不会额外生成 platform:feishu 权限
        Set<String> planned = MenuDomainService.planPermissionCatalog(MODULE_ROOTS).stream()
                .map(permission -> permission.getId().getCode())
                .filter(code -> code.startsWith("platform:feishu"))
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(Set.of(VIEW_CODE, EDIT_CODE, CONFIG_CODE), planned);
    }

    @Test
    void platformMenuCodesAndPermissionsStayUnique() {
        List<Menu> all = MODULE_ROOTS.stream().flatMap(root -> flatten(root).stream()).toList();
        Set<String> codes = new LinkedHashSet<>();
        for (Menu menu : all) {
            assertTrue(codes.add(menu.getCode()), "重复菜单码：" + menu.getCode());
        }
        Set<String> capabilityMenuCodes = all.stream()
                .filter(menu -> menu.getType() == MenuNodeType.MENU)
                .map(Menu::getCode)
                .filter(code -> code.startsWith("platform:"))
                .collect(Collectors.toCollection(TreeSet::new));
        assertTrue(capabilityMenuCodes.contains(MENU_CODE));
    }

    @Test
    void menuIconIsAcceptedByTheYdIconCatalog() throws IOException {
        String icon = node(MENU_CODE).getIcon();
        assertNotNull(icon);
        assertTrue(icon.startsWith("i-ri:"), "平台菜单图标必须使用 YD 图标前缀 i-ri：");
        String iconName = icon.substring("i-ri:".length());

        Path catalog = ydIconCatalog();
        assumeTrue(catalog != null, "前端工作区不在当前 checkout，跳过 YD 图标目录校验");
        String content = Files.readString(catalog, StandardCharsets.UTF_8);
        assertTrue(content.contains("'" + iconName + "'"), "图标 " + icon + " 不在 YD 图标目录中");
    }

    /**
     * 能力闸门联动：能力关闭时菜单与按钮一并隐藏；能力开启但用户无查看权限时菜单仍不可见。
     */
    @Test
    void feishuMenuFollowsCapabilityGateAndViewPermission() {
        CapabilityModuleRepo capabilityRepo = mock(CapabilityModuleRepo.class);
        MenuAppService service = new MenuAppService(mock(MenuDomainService.class), mock(MenuRepo.class), capabilityRepo);
        List<Menu> menus = List.of(node("platform:connection-hub"), node(MENU_CODE));

        when(capabilityRepo.findByCode("feishu")).thenReturn(Optional.of(capability(false)));
        Set<String> runtimeWhenDisabled = service.runtimeAvailableMenuCodes(menus);
        assertFalse(runtimeWhenDisabled.contains(MENU_CODE));
        assertFalse(service.visibleMenuCodes(Set.of(VIEW_CODE), menus, runtimeWhenDisabled).contains(MENU_CODE));

        when(capabilityRepo.findByCode("feishu")).thenReturn(Optional.of(capability(true)));
        Set<String> runtimeWhenEnabled = service.runtimeAvailableMenuCodes(menus);
        assertTrue(runtimeWhenEnabled.contains(MENU_CODE));
        assertTrue(service.visibleMenuCodes(Set.of(VIEW_CODE), menus, runtimeWhenEnabled).contains(MENU_CODE));
        // 有查看权限才看得到入口：没有 platform:feishu:view 时菜单不显示
        assertFalse(service.visibleMenuCodes(Set.of("platform:feishu:edit"), menus, runtimeWhenEnabled).contains(MENU_CODE));
    }

    /** 控制器 @PermissionRegister 注册的权限码（endpoint 登记优先于菜单补缺）。 */
    private static Set<String> controllerPermissionCodes() {
        Set<String> codes = new TreeSet<>();
        for (Class<?> controller : List.of(FeishuCloudDocController.class, FeishuQuotaController.class)) {
            for (Method method : controller.getDeclaredMethods()) {
                PermissionRegister register = method.getAnnotation(PermissionRegister.class);
                if (register != null && !register.code().isBlank()) {
                    codes.add(register.code());
                }
            }
        }
        return codes;
    }

    private static Menu node(String code) {
        return MODULE_ROOTS.stream()
                .flatMap(root -> flatten(root).stream())
                .filter(menu -> code.equals(menu.getCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("菜单种子缺少节点：" + code));
    }

    private static CapabilityModule capability(boolean enabled) {
        return CapabilityModule.builder()
                .code("feishu")
                .name("飞书开放平台")
                .enabled(enabled)
                .build();
    }

    private static List<Menu> flatten(Menu menu) {
        List<Menu> all = new ArrayList<>();
        all.add(menu);
        if (menu.getChildren() != null) {
            menu.getChildren().forEach(child -> all.addAll(flatten(child)));
        }
        return all;
    }

    /** 从当前工作目录向上寻找前端图标目录文件，找不到返回 null。 */
    private static Path ydIconCatalog() {
        Path relative = Path.of("yudream-frontend", "packages", "components", "src", "basic", "icon-picker", "icons.ts");
        Path current = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 4 && current != null; depth++) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }
}
