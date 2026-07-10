package online.yudream.base.application.system.menu;

import online.yudream.base.application.system.menu.cmd.MenuCreateCmd;
import online.yudream.base.application.system.menu.cmd.MenuUpdateCmd;
import online.yudream.base.application.system.menu.dto.MenuManageDTO;
import online.yudream.base.application.system.menu.query.MenuTreeQuery;
import online.yudream.base.application.system.menu.service.MenuAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.domain.system.menu.repo.MenuRepo;
import online.yudream.base.domain.system.menu.service.MenuDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MenuAppServicePluginMenuTest {

    @Mock
    private MenuDomainService menuDomainService;
    @Mock
    private MenuRepo menuRepo;
    @Mock
    private CapabilityModuleRepo capabilityModuleRepo;

    private MenuAppService service;

    @BeforeEach
    void setUp() {
        service = new MenuAppService(menuDomainService, menuRepo, capabilityModuleRepo);
    }

    @Test
    void managementTreeShowsAvailablePluginMenusUnderSystemParents() {
        Menu systemParent = systemMenu("system:tools", null, MenuNodeType.CATEGORY);
        Menu pluginMenu = pluginMenu("plugin:wallet:home", systemParent.getCode(), true);
        when(menuRepo.findAll()).thenReturn(List.of(systemParent, pluginMenu));

        List<MenuManageDTO> tree = service.tree(new MenuTreeQuery());

        assertThat(tree).singleElement().satisfies(parent -> {
            assertThat(parent.getCode()).isEqualTo(systemParent.getCode());
            assertThat(parent.getChildren()).singleElement().satisfies(child -> {
                assertThat(child.getCode()).isEqualTo(pluginMenu.getCode());
                assertThat(child.getParentCode()).isEqualTo(systemParent.getCode());
                assertThat(child.getDisplayParentCode()).isEqualTo(systemParent.getCode());
                assertThat(child.getSource()).isEqualTo(MenuSource.PLUGIN);
                assertThat(child.getPluginCode()).isEqualTo("wallet");
                assertThat(child.getPluginModuleName()).isEqualTo("wallet-admin");
                assertThat(child.getRuntimeAvailable()).isTrue();
            });
        });
    }

    @Test
    void managementTreeHidesUnavailablePluginMenus() {
        Menu systemParent = systemMenu("system:tools", null, MenuNodeType.CATEGORY);
        Menu unavailablePlugin = pluginMenu("plugin:wallet:home", systemParent.getCode(), false);
        when(menuRepo.findAll()).thenReturn(List.of(systemParent, unavailablePlugin));

        List<MenuManageDTO> tree = service.tree(new MenuTreeQuery());

        assertThat(tree).singleElement().satisfies(parent -> {
            assertThat(parent.getCode()).isEqualTo(systemParent.getCode());
            assertThat(parent.getChildren()).isEmpty();
        });
    }

    @Test
    void managementTreeShowsPluginMenuUnderAnotherPluginMenu() {
        Menu pluginParent = pluginMenu("plugin:wallet:tools", null, true);
        pluginParent.setType(MenuNodeType.CATEGORY);
        Menu pluginChild = pluginMenu("plugin:report:wallet", pluginParent.getCode(), true);
        pluginChild.setPluginCode("report");
        when(menuRepo.findAll()).thenReturn(List.of(pluginParent, pluginChild));

        List<MenuManageDTO> tree = service.tree(new MenuTreeQuery());

        assertThat(tree).singleElement().satisfies(parent ->
                assertThat(parent.getChildren()).singleElement().satisfies(child -> {
                    assertThat(child.getCode()).isEqualTo(pluginChild.getCode());
                    assertThat(child.getParentCode()).isEqualTo(pluginParent.getCode());
                }));
    }

    @Test
    void managementTreeReparentsAvailableChildPastUnavailablePluginAncestor() {
        Menu systemParent = systemMenu("system:tools", null, MenuNodeType.CATEGORY);
        Menu unavailablePlugin = pluginMenu("plugin:wallet:tools", systemParent.getCode(), false);
        unavailablePlugin.setType(MenuNodeType.LAYOUT);
        Menu availableChild = pluginMenu("plugin:report:wallet", unavailablePlugin.getCode(), true);
        availableChild.setPluginCode("report");
        when(menuRepo.findAll()).thenReturn(List.of(systemParent, unavailablePlugin, availableChild));

        List<MenuManageDTO> tree = service.tree(new MenuTreeQuery());

        assertThat(tree).singleElement().satisfies(parent -> {
            assertThat(parent.getCode()).isEqualTo(systemParent.getCode());
            assertThat(parent.getChildren()).singleElement().satisfies(child -> {
                assertThat(child.getCode()).isEqualTo(availableChild.getCode());
                assertThat(child.getParentCode()).isEqualTo(unavailablePlugin.getCode());
                assertThat(child.getDisplayParentCode()).isEqualTo(systemParent.getCode());
            });
        });
        assertThat(availableChild.getParentCode()).isEqualTo(unavailablePlugin.getCode());
    }

    @Test
    void managementTreeIncludesAvailableAncestorsOfKeywordMatches() {
        Menu systemParent = systemMenu("system:tools", null, MenuNodeType.CATEGORY);
        systemParent.setName("工具中心");
        Menu matchingChild = pluginMenu("plugin:wallet:home", systemParent.getCode(), true);
        matchingChild.setName("钱包控制台");
        when(menuRepo.findAll()).thenReturn(List.of(systemParent, matchingChild));
        MenuTreeQuery query = new MenuTreeQuery();
        query.setKeyword("钱包");

        List<MenuManageDTO> tree = service.tree(query);

        assertThat(tree).singleElement().satisfies(parent -> {
            assertThat(parent.getCode()).isEqualTo(systemParent.getCode());
            assertThat(parent.getChildren()).extracting(MenuManageDTO::getCode)
                    .containsExactly(matchingChild.getCode());
        });
    }

    @Test
    void routeTreeIncludesAvailablePluginMenusWithRuntimeMetadata() {
        Menu systemParent = systemMenu("system:tools", null, MenuNodeType.CATEGORY);
        Menu systemChild = systemMenu("system:tools:audit", systemParent.getCode(), MenuNodeType.MENU);
        Menu pluginChild = pluginMenu("plugin:wallet:home", systemParent.getCode(), true);
        when(menuRepo.findAll()).thenReturn(List.of(systemParent, systemChild, pluginChild));
        when(menuDomainService.findActiveMenus()).thenReturn(List.of(systemParent, systemChild, pluginChild));

        List<Map<String, Object>> routes = service.buildRouteTree(List.of("*"));

        assertThat(routes).singleElement().satisfies(group -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) group.get("children");
            assertThat(children).extracting(child -> child.get("name"))
                    .containsExactly(pluginChild.getCode(), systemChild.getCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) children.get(1).get("meta");
            assertThat(meta.get("menuCode")).isEqualTo(systemChild.getCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> pluginMeta = (Map<String, Object>) children.getFirst().get("meta");
            assertThat(pluginMeta.get("pluginCode")).isEqualTo("wallet");
            assertThat(pluginMeta.get("pluginModuleName")).isEqualTo("wallet-admin");
        });
    }

    @Test
    void routePermissionIncludesPluginStructuralAncestorsWithoutSyntheticPermissions() {
        Menu module = pluginMenu("plugin:wallet:module:walletAdmin", null, true);
        module.setType(MenuNodeType.CATEGORY);
        module.setPermission(null);
        Menu layout = pluginMenu("plugin:wallet:parent:walletAdmin:/wallet", module.getCode(), true);
        layout.setType(MenuNodeType.LAYOUT);
        layout.setPath("/wallet");
        layout.setComponent("Layout");
        layout.setPermission(null);
        Menu route = pluginMenu("plugin:wallet:route:walletAdmin:transactions", layout.getCode(), true);
        route.setType(MenuNodeType.MENU);
        route.setPath("/wallet/transactions");
        route.setComponent("wallet/Transactions");
        route.setPermission("plugin:wallet:transaction:list");
        List<Menu> menus = List.of(module, layout, route);
        when(menuDomainService.findActiveMenus()).thenReturn(menus);
        when(menuRepo.findAll()).thenReturn(menus);

        List<Map<String, Object>> routes = service.buildRouteTree(List.of("plugin:wallet:transaction:list"));

        assertThat(routes).singleElement().satisfies(root -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> layouts = (List<Map<String, Object>>) root.get("children");
            assertThat(layouts).singleElement().satisfies(layoutRoute -> {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> pages = (List<Map<String, Object>>) layoutRoute.get("children");
                assertThat(pages).extracting(page -> page.get("name")).containsExactly(route.getCode());
            });
        });
    }

    @Test
    void routeTreeExcludesOnlyTheUnavailablePluginNodeNotItsMovedChildren() {
        Menu systemParent = systemMenu("system:tools", null, MenuNodeType.CATEGORY);
        Menu unavailableOwner = pluginMenu("plugin:wallet:tools", systemParent.getCode(), false);
        unavailableOwner.setType(MenuNodeType.LAYOUT);
        Menu movedChild = pluginMenu("plugin:report:wallet", systemParent.getCode(), true);
        movedChild.setPluginCode("report");
        movedChild.setPluginModuleName("report-admin");
        movedChild.setComponent("report/Wallet");
        when(menuRepo.findAll()).thenReturn(List.of(systemParent, unavailableOwner, movedChild));
        when(menuDomainService.findActiveMenus()).thenReturn(List.of(systemParent, unavailableOwner, movedChild));

        List<Map<String, Object>> routes = service.buildRouteTree(List.of("*"));

        assertThat(routes).singleElement().satisfies(group -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) group.get("children");
            assertThat(children).extracting(child -> child.get("name"))
                    .containsExactly(movedChild.getCode());
        });
    }

    @Test
    void routePermissionSkipsUnavailablePluginAncestorForForeignPluginChild() {
        Menu systemRoot = systemMenu("system:tools", null, MenuNodeType.CATEGORY);
        systemRoot.setPermission(null);
        Menu unavailableParent = pluginMenu("plugin:wallet:module:walletAdmin", systemRoot.getCode(), false);
        unavailableParent.setType(MenuNodeType.CATEGORY);
        Menu foreignChild = pluginMenu("plugin:alipay:route:alipayAdmin:settings", unavailableParent.getCode(), true);
        foreignChild.setPluginCode("alipay");
        foreignChild.setType(MenuNodeType.MENU);
        foreignChild.setPath("/platform/plugins/alipay/settings");
        foreignChild.setComponent("alipay/Settings");
        foreignChild.setPermission("plugin:alipay:settings:view");
        List<Menu> menus = List.of(systemRoot, unavailableParent, foreignChild);
        when(menuDomainService.findActiveMenus()).thenReturn(menus);
        when(menuRepo.findAll()).thenReturn(menus);

        List<Map<String, Object>> routes = service.buildRouteTree(List.of("plugin:alipay:settings:view"));

        assertThat(routes).singleElement().satisfies(root -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) root.get("children");
            assertThat(children).extracting(child -> child.get("name")).containsExactly(foreignChild.getCode());
        });
    }

    @Test
    void routeTreeKeepsPersistedPluginLayoutAndItsSystemChild() {
        Menu systemParent = systemMenu("system:tools", null, MenuNodeType.CATEGORY);
        Menu pluginParent = pluginMenu("plugin:wallet:tools", systemParent.getCode(), true);
        pluginParent.setType(MenuNodeType.LAYOUT);
        Menu legacySystemChild = systemMenu(
                "system:tools:legacy-report", pluginParent.getCode(), MenuNodeType.MENU);
        when(menuRepo.findAll()).thenReturn(List.of(systemParent, pluginParent, legacySystemChild));
        when(menuDomainService.findActiveMenus()).thenReturn(List.of(systemParent, pluginParent, legacySystemChild));

        List<Map<String, Object>> routes = service.buildRouteTree(List.of("*"));

        assertThat(routes).singleElement().satisfies(group -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) group.get("children");
            assertThat(children).extracting(child -> child.get("name"))
                    .containsExactly(pluginParent.getCode());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nested = (List<Map<String, Object>>) children.getFirst().get("children");
            assertThat(nested).extracting(child -> child.get("name"))
                    .containsExactly(legacySystemChild.getCode());
        });
    }

    @Test
    void staticRouteTreeClimbsPastDisabledLegacyPluginParentToSystemAncestor() {
        Menu systemParent = systemMenu("system:tools", null, MenuNodeType.CATEGORY);
        Menu disabledPluginParent = pluginMenu("plugin:wallet:tools", systemParent.getCode(), false);
        disabledPluginParent.setType(MenuNodeType.LAYOUT);
        Menu legacySystemChild = systemMenu(
                "system:tools:legacy-report", disabledPluginParent.getCode(), MenuNodeType.MENU);
        when(menuRepo.findAll()).thenReturn(List.of(systemParent, disabledPluginParent, legacySystemChild));
        when(menuDomainService.findActiveMenus()).thenReturn(List.of(systemParent, legacySystemChild));

        List<Map<String, Object>> routes = service.buildRouteTree(List.of("*"));

        assertThat(routes).singleElement().satisfies(group -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) group.get("children");
            assertThat(children).extracting(child -> child.get("name"))
                    .containsExactly(legacySystemChild.getCode());
        });
    }

    @Test
    void staticRouteTreeClimbsPastDisabledSystemParentToVisibleSystemAncestor() {
        Menu systemRoot = systemMenu("system:root", null, MenuNodeType.CATEGORY);
        Menu disabledSystemParent = systemMenu("system:hidden", systemRoot.getCode(), MenuNodeType.CATEGORY);
        disabledSystemParent.setStatus(MenuStatus.DISABLED);
        Menu activeSystemChild = systemMenu("system:visible", disabledSystemParent.getCode(), MenuNodeType.MENU);
        when(menuRepo.findAll()).thenReturn(List.of(systemRoot, disabledSystemParent, activeSystemChild));
        when(menuDomainService.findActiveMenus()).thenReturn(List.of(systemRoot, activeSystemChild));

        List<Map<String, Object>> routes = service.buildRouteTree(List.of("*"));

        assertThat(routes).singleElement().satisfies(group -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) group.get("children");
            assertThat(children).extracting(child -> child.get("name"))
                    .containsExactly(activeSystemChild.getCode());
        });
    }

    @Test
    void staticRouteTreeNormalizesMissingParentChainToRoot() {
        Menu activeSystemChild = systemMenu("system:orphan", "system:missing", MenuNodeType.MENU);
        when(menuRepo.findAll()).thenReturn(List.of(activeSystemChild));
        when(menuDomainService.findActiveMenus()).thenReturn(List.of(activeSystemChild));

        List<Map<String, Object>> routes = service.buildRouteTree(List.of("*"));

        assertThat(routes).singleElement().satisfies(route ->
                assertThat(route.get("name")).isEqualTo(activeSystemChild.getCode()));
    }

    @Test
    void routeTreeKeepsPluginRootWithItsPersistedSystemChild() {
        Menu pluginParent = pluginMenu("plugin:wallet:tools", null, true);
        pluginParent.setType(MenuNodeType.CATEGORY);
        Menu legacySystemChild = systemMenu(
                "system:legacy-report", pluginParent.getCode(), MenuNodeType.MENU);
        when(menuRepo.findAll()).thenReturn(List.of(pluginParent, legacySystemChild));
        when(menuDomainService.findActiveMenus()).thenReturn(List.of(pluginParent, legacySystemChild));

        List<Map<String, Object>> routes = service.buildRouteTree(List.of("*"));

        assertThat(routes).singleElement().satisfies(route -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) route.get("children");
            assertThat(children).extracting(child -> child.get("name"))
                    .containsExactly(legacySystemChild.getCode());
        });
    }

    @Test
    void createSystemMenuAllowsPluginParentWithoutChangingOwnership() {
        Menu pluginParent = pluginMenu("plugin:wallet:tools", null, true);
        when(menuRepo.existsByCode("system:report")).thenReturn(false);
        when(menuRepo.findByCode(pluginParent.getCode())).thenReturn(Optional.of(pluginParent));
        MenuCreateCmd cmd = new MenuCreateCmd();
        cmd.setCode("system:report");
        cmd.setName("系统报表");
        cmd.setType(MenuNodeType.MENU);
        cmd.setParentCode(pluginParent.getCode());
        when(menuDomainService.syncMenu(any(Menu.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MenuManageDTO created = service.create(cmd);

        assertThat(created.getParentCode()).isEqualTo(pluginParent.getCode());
        assertThat(created.getSource()).isEqualTo(MenuSource.SYSTEM);
        assertThat(created.getPluginCode()).isNull();
    }

    @Test
    void updateSystemMenuAllowsPluginParentWithoutChangingOwnership() {
        Menu systemMenu = systemMenu("system:report", null, MenuNodeType.MENU);
        Menu pluginParent = pluginMenu("plugin:wallet:tools", null, true);
        when(menuRepo.findByCode(systemMenu.getCode())).thenReturn(Optional.of(systemMenu));
        when(menuRepo.findByCode(pluginParent.getCode())).thenReturn(Optional.of(pluginParent));
        MenuUpdateCmd cmd = new MenuUpdateCmd();
        cmd.setCode(systemMenu.getCode());
        cmd.setName("系统报表");
        cmd.setType(MenuNodeType.MENU);
        cmd.setParentCode(pluginParent.getCode());
        when(menuDomainService.syncMenu(any(Menu.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MenuManageDTO updated = service.update(cmd);

        assertThat(updated.getParentCode()).isEqualTo(pluginParent.getCode());
        assertThat(updated.getSource()).isEqualTo(MenuSource.SYSTEM);
        assertThat(updated.getPluginCode()).isNull();
    }

    @Test
    void updateKeepsPluginOwnershipMetadata() {
        Menu pluginMenu = pluginMenu("plugin:wallet:home", "system:tools", true);
        String registrationKey = pluginMenu.getPluginRegistrationKey();
        when(menuRepo.findByCode(pluginMenu.getCode())).thenReturn(Optional.of(pluginMenu));
        when(menuRepo.findByCode("system:reports")).thenReturn(Optional.of(systemMenu(
                "system:reports", null, MenuNodeType.CATEGORY)));
        when(menuDomainService.syncMenu(any(Menu.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MenuUpdateCmd cmd = new MenuUpdateCmd();
        cmd.setCode(pluginMenu.getCode());
        cmd.setName("钱包报表");
        cmd.setType(MenuNodeType.MENU);
        cmd.setParentCode("system:reports");
        cmd.setModule("custom-module");
        cmd.setIcon("custom-icon");
        cmd.setPath("/reports/wallet");
        cmd.setComponent("WalletReport");
        cmd.setLink("https://example.test/wallet");
        cmd.setSort(88);
        cmd.setVisible(false);
        cmd.setPermission("wallet:report");
        cmd.setStatus(MenuStatus.DISABLED);

        MenuManageDTO updated = service.update(cmd);

        assertThat(updated.getName()).isEqualTo("钱包报表");
        assertThat(updated.getType()).isEqualTo(MenuNodeType.MENU);
        assertThat(updated.getParentCode()).isEqualTo("system:reports");
        assertThat(updated.getModule()).isEqualTo("custom-module");
        assertThat(updated.getIcon()).isEqualTo("custom-icon");
        assertThat(updated.getPath()).isEqualTo("/reports/wallet");
        assertThat(updated.getComponent()).isEqualTo("WalletReport");
        assertThat(updated.getLink()).isEqualTo("https://example.test/wallet");
        assertThat(updated.getSort()).isEqualTo(88);
        assertThat(updated.getVisible()).isFalse();
        assertThat(updated.getPermission()).isEqualTo("wallet:report");
        assertThat(updated.getStatus()).isEqualTo(MenuStatus.DISABLED);
        assertThat(updated.getSource()).isEqualTo(MenuSource.PLUGIN);
        assertThat(updated.getPluginCode()).isEqualTo("wallet");
        assertThat(updated.getPluginModuleName()).isEqualTo("wallet-admin");
        assertThat(updated.getRuntimeAvailable()).isTrue();
        assertThat(pluginMenu.getPluginRegistrationKey()).isEqualTo(registrationKey);
    }

    @Test
    void enableOnlyActivatesMenuAndKeepsConfiguration() {
        Menu menu = pluginMenu("plugin:wallet/pages/home", "system:tools", true);
        menu.setName("Edited wallet");
        menu.setPath("/custom/wallet");
        menu.setPermission("wallet:custom");
        menu.disable();
        when(menuRepo.findByCode(menu.getCode())).thenReturn(Optional.of(menu));
        when(menuRepo.save(menu)).thenReturn(menu);

        service.enable(menu.getCode());

        assertThat(menu.getStatus()).isEqualTo(MenuStatus.ACTIVE);
        assertThat(menu.getName()).isEqualTo("Edited wallet");
        assertThat(menu.getPath()).isEqualTo("/custom/wallet");
        assertThat(menu.getPermission()).isEqualTo("wallet:custom");
        assertThat(menu.getParentCode()).isEqualTo("system:tools");
        verify(menuRepo).save(menu);
    }

    private Menu systemMenu(String code, String parentCode, MenuNodeType type) {
        return Menu.builder()
                .code(code)
                .name(code)
                .type(type)
                .parentCode(parentCode)
                .path("/" + code.replace(':', '/'))
                .component(type == MenuNodeType.MENU ? "SystemPage" : null)
                .sort(1)
                .visible(true)
                .status(MenuStatus.ACTIVE)
                .source(MenuSource.SYSTEM)
                .build();
    }

    private Menu pluginMenu(String code, String parentCode, boolean runtimeAvailable) {
        return Menu.builder()
                .code(code)
                .name("Wallet")
                .type(MenuNodeType.MENU)
                .parentCode(parentCode)
                .module("wallet")
                .path("/wallet")
                .component("WalletHome")
                .sort(2)
                .visible(true)
                .status(MenuStatus.ACTIVE)
                .source(MenuSource.PLUGIN)
                .pluginCode("wallet")
                .pluginModuleName("wallet-admin")
                .pluginRegistrationKey("route:wallet-admin:home")
                .runtimeAvailable(runtimeAvailable)
                .build();
    }
}
