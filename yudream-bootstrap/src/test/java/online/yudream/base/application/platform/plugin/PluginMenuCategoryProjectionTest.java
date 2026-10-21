package online.yudream.base.application.platform.plugin;

import online.yudream.base.application.platform.plugin.service.PluginMenuProjectionService;
import online.yudream.base.application.system.menu.service.MenuAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.enumerate.PluginMenuMode;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendModuleInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendRouteInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginMenuCategory;
import online.yudream.base.domain.platform.plugin.valobj.PluginMenuPolicy;
import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.domain.system.menu.repo.MenuRepo;
import online.yudream.base.domain.system.menu.service.MenuDomainService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginMenuCategoryProjectionTest {

    private final InMemoryMenuRepo repo = new InMemoryMenuRepo();
    private final PluginMenuProjectionService service = new PluginMenuProjectionService(repo);

    @Test
    void unrelatedPluginsShareOneBusinessCategoryAndKeepTheirStableMenuCodes() {
        project("server", categorized("mc"));
        project("skin", categorized("mc"));

        Menu category = menu("plugin-category:mc");
        assertEquals("MC", category.getName());
        assertEquals(MenuSource.SYSTEM, category.getSource());
        assertEquals(MenuNodeType.CATEGORY, category.getType());
        assertNull(category.getPluginCode());
        assertNull(category.getParentCode());
        assertNull(category.getPermission());
        assertNull(category.getPath());
        assertNull(category.getComponent());
        assertEquals("plugin-category:mc", moduleMenu("server").getParentCode());
        assertEquals("plugin-category:mc", moduleMenu("skin").getParentCode());
        assertEquals(MenuNodeType.CATEGORY, moduleMenu("skin").getType());
        assertNull(moduleMenu("skin").getPath());
        assertEquals("module:admin", moduleMenu("skin").getPluginRegistrationKey());
        assertEquals(1, repo.findAll().stream().filter(menu -> menu.getParentCode() == null).count());
    }

    @Test
    void disablingOnePluginKeepsTheCategoryAndOtherAuthorizedPluginRoutes() {
        project("server", categorized("mc"));
        project("skin", categorized("mc"));
        service.markUnavailable("server");

        Menu category = menu("plugin-category:mc");
        assertTrue(category.isAvailableForRuntime());
        assertTrue(moduleMenu("skin").isAvailableForRuntime());
        MenuAppService navigation = new MenuAppService(new MenuDomainService(repo, null), repo, null);
        List<Map<String, Object>> routes = navigation.buildRouteTree(List.of("plugin:skin:view"));
        assertEquals(1, routes.size());
        assertEquals("MC", meta(routes.getFirst()).get("title"));
        assertEquals(1, children(routes.getFirst()).size());
        Map<String, Object> module = children(routes.getFirst()).getFirst();
        assertEquals("plugin:skin:module:admin", meta(module).get("menuCode"));
        assertEquals("plugin:skin:route:admin:home", children(module).getFirst().get("name"));

        service.markUnavailable("skin");
        assertTrue(category.isAvailableForRuntime());
        assertTrue(navigation.buildRouteTree(List.of("*")).isEmpty());
    }

    @Test
    void oldDeclarationsAndExplicitIndependentExceptionsKeepTheirRootMenus() {
        project("old", PluginMenuPolicy.legacy());
        project("solo", new PluginMenuPolicy(PluginMenuMode.INDEPENDENT, PluginMenuCategory.named("mc")));
        assertNull(moduleMenu("old").getParentCode());
        assertNull(moduleMenu("solo").getParentCode());
        assertTrue(repo.findByCode("plugin-category:mc").isEmpty());
    }

    @Test
    void independentRootMenusMustFitTheFiveCharacterRailLabel() {
        PluginFrontendModuleInfo tooLong = new PluginFrontendModuleInfo("demo", "entry", "admin", "", "",
                "六个字的名字", "icon", 0, List.of(route("demo", false)))
                .withMenuPolicy(new PluginMenuPolicy(PluginMenuMode.INDEPENDENT, null));
        BizException error = assertThrows(BizException.class, () -> service.project("demo", List.of(tooLong)));
        assertTrue(error.getMessage().contains("5"));
    }

    @Test
    void explicitParentCodeTakesPriorityOverAutomaticClassification() {
        PluginFrontendModuleInfo declaration = new PluginFrontendModuleInfo("skin", "entry", "admin", "", "",
                "皮肤", "icon", 0, List.of(route("skin", false)), "custom:parent", true, MenuStatus.ACTIVE)
                .withMenuPolicy(categorized("mc"));
        service.project("skin", List.of(declaration));
        assertTrue(repo.findByCode("plugin:skin:module:admin").isEmpty());
        assertTrue(repo.findByCode("plugin-category:mc").isEmpty());
        assertEquals("custom:parent", menu("plugin:skin:route:admin:home").getParentCode());
    }

    @Test
    void upgradingToCategoryModePreservesAllPersistedMenuEditsWithoutCreatingAnUnusedRoot() {
        project("skin", PluginMenuPolicy.legacy());
        Menu module = moduleMenu("skin");
        module.setParentCode("custom:parent");
        module.setName("自定义名称");
        module.setType(MenuNodeType.LAYOUT);
        module.setVisible(false);
        module.setSort(999);
        project("skin", categorized("mc"));

        assertSame(module, moduleMenu("skin"));
        assertEquals("custom:parent", module.getParentCode());
        assertEquals("自定义名称", module.getName());
        assertEquals(MenuNodeType.LAYOUT, module.getType());
        assertFalse(module.getVisible());
        assertEquals(999, module.getSort());
        assertTrue(repo.findByCode("plugin-category:mc").isEmpty());
    }

    @Test
    void aPublicOnlyPluginAndAnEmptyFrontendModuleDoNotCreateBackendCategories() {
        PluginFrontendModuleInfo publicModule = module("public", List.of(route("public", true)))
                .withMenuPolicy(categorized("social"));
        service.project("public", List.of(publicModule));
        service.project("empty", List.of(module("empty", List.of()).withMenuPolicy(categorized("mc"))));
        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void becomingPublicOnlyRetiresPreviouslyProjectedModuleAndRoutes() {
        project("public", categorized("social"));
        service.project("public", List.of(module("public", List.of(route("public", true)))
                .withMenuPolicy(categorized("social"))));
        assertFalse(moduleMenu("public").getRuntimeAvailable());
        assertFalse(menu("plugin:public:route:admin:home").getRuntimeAvailable());
        assertTrue(menu("plugin-category:social").isAvailableForRuntime());
    }

    @Test
    void laterPluginsReusePersistedCategoryMetadataWithoutOverwritingIt() {
        project("first", new PluginMenuPolicy(PluginMenuMode.CATEGORY,
                new PluginMenuCategory("community", "社区", "custom-icon", 81)));
        Menu shared = menu("plugin-category:community");
        shared.setName("管理员分类名称");
        shared.setSort(93);
        project("second", new PluginMenuPolicy(PluginMenuMode.CATEGORY,
                new PluginMenuCategory("community", "另一个名字", "other-icon", 5)));
        assertEquals("管理员分类名称", shared.getName());
        assertEquals("custom-icon", shared.getIcon());
        assertEquals(93, shared.getSort());
        assertEquals(shared.getCode(), moduleMenu("second").getParentCode());
    }

    @Test
    void copiedFrontendDeclarationsPreserveTheirHostMenuPolicy() {
        PluginFrontendModuleInfo declaration = module("skin", List.of(route("skin", true)))
                .withMenuPolicy(categorized("mc"));
        assertEquals(declaration.menuPolicy(), declaration.withSortOverrides(17, declaration.routes()).menuPolicy());
        assertEquals(declaration.menuPolicy(), PluginFrontendModuleInfo.onlyPublicRoutes(declaration).menuPolicy());
    }

    private void project(String code, PluginMenuPolicy policy) {
        service.project(code, List.of(module(code, List.of(route(code, false))).withMenuPolicy(policy)));
    }

    private PluginFrontendModuleInfo module(String code, List<PluginFrontendRouteInfo> routes) {
        return new PluginFrontendModuleInfo(code, "entry", "admin", "", "", code, "icon", 0, routes);
    }

    private PluginFrontendRouteInfo route(String code, boolean publicAccess) {
        return new PluginFrontendRouteInfo("/" + code, "home", code, "icon", null, null, null, 0,
                code + "/Home", "plugin:" + code + ":view", 0, false, publicAccess);
    }

    private PluginMenuPolicy categorized(String code) {
        return new PluginMenuPolicy(PluginMenuMode.CATEGORY, PluginMenuCategory.named(code));
    }

    private Menu menu(String code) {
        return repo.findByCode(code).orElseThrow();
    }

    private Menu moduleMenu(String pluginCode) {
        return menu("plugin:" + pluginCode + ":module:admin");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> meta(Map<String, Object> route) {
        return (Map<String, Object>) route.get("meta");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> children(Map<String, Object> route) {
        return (List<Map<String, Object>>) route.get("children");
    }

    private static class InMemoryMenuRepo implements MenuRepo {
        private final Map<String, Menu> menus = new LinkedHashMap<>();
        @Override public Menu save(Menu menu) { menus.put(menu.getCode(), menu); return menu; }
        @Override public Optional<Menu> findById(Long id) {
            if (id == null) { return Optional.empty(); }
            return menus.values().stream().filter(menu -> id.equals(menu.getId())).findFirst();
        }
        @Override public Optional<Menu> findByCode(String code) { return Optional.ofNullable(menus.get(code)); }
        @Override public Optional<Menu> findByPluginCodeAndRegistrationKey(String code, String key) {
            return findByPluginCode(code).stream().filter(menu -> key.equals(menu.getPluginRegistrationKey())).findFirst();
        }
        @Override public List<Menu> findAll() { return List.copyOf(menus.values()); }
        @Override public List<Menu> findByPluginCode(String code) {
            return menus.values().stream().filter(Menu::isPluginMenu).filter(menu -> code.equals(menu.getPluginCode())).toList();
        }
        @Override public List<Menu> findByTypeIn(List<MenuNodeType> types) {
            return menus.values().stream().filter(menu -> types.contains(menu.getType())).toList();
        }
        @Override public boolean existsByCode(String code) { return menus.containsKey(code); }
        @Override public long count() { return menus.size(); }
        @Override public void deleteByCode(String code) { menus.remove(code); }
    }
}
