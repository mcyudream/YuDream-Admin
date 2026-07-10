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
                assertThat(child.getParentCode()).isEqualTo(systemParent.getCode());
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
    void staticRouteTreeNeverReturnsPluginMenus() {
        Menu systemParent = systemMenu("system:tools", null, MenuNodeType.CATEGORY);
        Menu systemChild = systemMenu("system:tools:audit", systemParent.getCode(), MenuNodeType.MENU);
        Menu pluginChild = pluginMenu("plugin:wallet:home", systemParent.getCode(), true);
        when(menuDomainService.findActiveMenus()).thenReturn(List.of(systemParent, systemChild, pluginChild));

        List<Map<String, Object>> routes = service.buildRouteTree(List.of("*"));

        assertThat(routes).singleElement().satisfies(group -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) group.get("children");
            assertThat(children).extracting(child -> child.get("name"))
                    .containsExactly(systemChild.getCode());
        });
    }

    @Test
    void staticRouteTreeClimbsPastLegacyPluginParentToSystemAncestor() {
        Menu systemParent = systemMenu("system:tools", null, MenuNodeType.CATEGORY);
        Menu pluginParent = pluginMenu("plugin:wallet:tools", systemParent.getCode(), true);
        pluginParent.setType(MenuNodeType.LAYOUT);
        Menu legacySystemChild = systemMenu(
                "system:tools:legacy-report", pluginParent.getCode(), MenuNodeType.MENU);
        when(menuDomainService.findActiveMenus()).thenReturn(List.of(systemParent, pluginParent, legacySystemChild));

        List<Map<String, Object>> routes = service.buildRouteTree(List.of("*"));

        assertThat(routes).singleElement().satisfies(group -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) group.get("children");
            assertThat(children).extracting(child -> child.get("name"))
                    .containsExactly(legacySystemChild.getCode());
        });
    }

    @Test
    void staticRouteTreeNormalizesLegacySystemMenuWithoutSystemAncestorToRoot() {
        Menu pluginParent = pluginMenu("plugin:wallet:tools", null, true);
        pluginParent.setType(MenuNodeType.CATEGORY);
        Menu legacySystemChild = systemMenu(
                "system:legacy-report", pluginParent.getCode(), MenuNodeType.MENU);
        when(menuDomainService.findActiveMenus()).thenReturn(List.of(pluginParent, legacySystemChild));

        List<Map<String, Object>> routes = service.buildRouteTree(List.of("*"));

        assertThat(routes).singleElement().satisfies(route ->
                assertThat(route.get("name")).isEqualTo(legacySystemChild.getCode()));
    }

    @Test
    void createSystemMenuRejectsPluginParent() {
        Menu pluginParent = pluginMenu("plugin:wallet:tools", null, true);
        when(menuRepo.existsByCode("system:report")).thenReturn(false);
        when(menuRepo.findByCode(pluginParent.getCode())).thenReturn(Optional.of(pluginParent));
        MenuCreateCmd cmd = new MenuCreateCmd();
        cmd.setCode("system:report");
        cmd.setName("系统报表");
        cmd.setType(MenuNodeType.MENU);
        cmd.setParentCode(pluginParent.getCode());

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(BizException.class);
    }

    @Test
    void updateSystemMenuRejectsPluginParent() {
        Menu systemMenu = systemMenu("system:report", null, MenuNodeType.MENU);
        Menu pluginParent = pluginMenu("plugin:wallet:tools", null, true);
        when(menuRepo.findByCode(systemMenu.getCode())).thenReturn(Optional.of(systemMenu));
        when(menuRepo.findByCode(pluginParent.getCode())).thenReturn(Optional.of(pluginParent));
        MenuUpdateCmd cmd = new MenuUpdateCmd();
        cmd.setCode(systemMenu.getCode());
        cmd.setName("系统报表");
        cmd.setType(MenuNodeType.MENU);
        cmd.setParentCode(pluginParent.getCode());

        assertThatThrownBy(() -> service.update(cmd))
                .isInstanceOf(BizException.class);
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
