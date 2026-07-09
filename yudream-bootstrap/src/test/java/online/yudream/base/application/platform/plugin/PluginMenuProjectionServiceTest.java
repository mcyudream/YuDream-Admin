package online.yudream.base.application.platform.plugin;

import online.yudream.base.application.platform.plugin.service.PluginMenuProjectionService;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendModuleInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendRouteInfo;
import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.domain.system.menu.repo.MenuRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginMenuProjectionServiceTest {

    private static final String PLUGIN_CODE = "yudream-wallet";
    private static final String MODULE_NAME = "walletAdmin";

    @Mock
    private MenuRepo menuRepo;

    private PluginMenuProjectionService service;

    @BeforeEach
    void setUp() {
        service = new PluginMenuProjectionService(menuRepo);
        when(menuRepo.findByPluginCode(PLUGIN_CODE)).thenReturn(List.of());
        when(menuRepo.findByPluginCodeAndRegistrationKey(any(), any())).thenReturn(Optional.empty());
        when(menuRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsModuleParentAndRouteMenusFromFrontendDeclaration() {
        service.project(PLUGIN_CODE, List.of(module(route(
                "/wallet/transactions",
                "walletTransactions",
                "交易记录",
                "/wallet",
                "钱包管理"
        ))));

        ArgumentCaptor<Menu> captor = ArgumentCaptor.forClass(Menu.class);
        verify(menuRepo, org.mockito.Mockito.times(3)).save(captor.capture());
        List<Menu> saved = captor.getAllValues();

        Menu module = menuByKey(saved, "module:" + MODULE_NAME);
        assertThat(module.getCode()).isEqualTo("plugin:yudream-wallet:module:walletAdmin");
        assertThat(module.getName()).isEqualTo("钱包中心");
        assertThat(module.getType()).isEqualTo(MenuNodeType.CATEGORY);
        assertThat(module.getParentCode()).isNull();
        assertOwnership(module, "module:" + MODULE_NAME);

        Menu parent = menuByKey(saved, "parent:" + MODULE_NAME + ":/wallet");
        assertThat(parent.getType()).isEqualTo(MenuNodeType.LAYOUT);
        assertThat(parent.getParentCode()).isEqualTo(module.getCode());
        assertThat(parent.getPath()).isEqualTo("/wallet");
        assertOwnership(parent, "parent:" + MODULE_NAME + ":/wallet");

        Menu route = menuByKey(saved, "route:" + MODULE_NAME + ":walletTransactions");
        assertThat(route.getType()).isEqualTo(MenuNodeType.MENU);
        assertThat(route.getParentCode()).isEqualTo(parent.getCode());
        assertThat(route.getPath()).isEqualTo("/wallet/transactions");
        assertThat(route.getComponent()).isEqualTo("wallet/transactions/index");
        assertThat(route.getPermission()).isEqualTo("wallet:transaction:list");
        assertOwnership(route, "route:" + MODULE_NAME + ":walletTransactions");
    }

    @Test
    void keepsEditedFieldsWhenTheSameRegistrationReturns() {
        Menu edited = Menu.builder()
                .code("plugin:yudream-wallet:route:walletAdmin:walletTransactions")
                .name("自定义名称")
                .type(MenuNodeType.LINK)
                .parentCode("system:dashboard")
                .module("custom-module")
                .icon("custom-icon")
                .path("/custom-path")
                .component("custom/component")
                .link("https://example.test")
                .sort(91)
                .visible(false)
                .permission("custom:permission")
                .status(MenuStatus.DISABLED)
                .source(MenuSource.PLUGIN)
                .pluginCode("old-plugin-code")
                .pluginModuleName("old-module")
                .pluginRegistrationKey("route:" + MODULE_NAME + ":walletTransactions")
                .runtimeAvailable(false)
                .build();
        when(menuRepo.findByPluginCodeAndRegistrationKey(
                PLUGIN_CODE,
                "route:" + MODULE_NAME + ":walletTransactions"
        )).thenReturn(Optional.of(edited));

        service.project(PLUGIN_CODE, List.of(module(route(
                "/wallet/transactions",
                "walletTransactions",
                "交易记录",
                "/wallet",
                "钱包管理"
        ))));

        assertThat(edited.getName()).isEqualTo("自定义名称");
        assertThat(edited.getType()).isEqualTo(MenuNodeType.LINK);
        assertThat(edited.getParentCode()).isEqualTo("system:dashboard");
        assertThat(edited.getModule()).isEqualTo("custom-module");
        assertThat(edited.getIcon()).isEqualTo("custom-icon");
        assertThat(edited.getPath()).isEqualTo("/custom-path");
        assertThat(edited.getComponent()).isEqualTo("custom/component");
        assertThat(edited.getLink()).isEqualTo("https://example.test");
        assertThat(edited.getSort()).isEqualTo(91);
        assertThat(edited.getVisible()).isFalse();
        assertThat(edited.getPermission()).isEqualTo("custom:permission");
        assertThat(edited.getStatus()).isEqualTo(MenuStatus.DISABLED);
        assertThat(edited.getSource()).isEqualTo(MenuSource.PLUGIN);
        assertThat(edited.getPluginCode()).isEqualTo(PLUGIN_CODE);
        assertThat(edited.getPluginModuleName()).isEqualTo(MODULE_NAME);
        assertThat(edited.getRuntimeAvailable()).isTrue();
    }

    @Test
    void marksRemovedDeclarationsUnavailableWithoutDeletingThem() {
        Menu current = pluginMenu("route:" + MODULE_NAME + ":walletTransactions", true);
        Menu removed = pluginMenu("route:" + MODULE_NAME + ":walletReports", true);
        when(menuRepo.findByPluginCode(PLUGIN_CODE)).thenReturn(List.of(current, removed));
        when(menuRepo.findByPluginCodeAndRegistrationKey(
                PLUGIN_CODE,
                "route:" + MODULE_NAME + ":walletTransactions"
        )).thenReturn(Optional.of(current));

        service.project(PLUGIN_CODE, List.of(module(route(
                "/wallet/transactions",
                "walletTransactions",
                "交易记录",
                null,
                null
        ))));

        assertThat(current.getRuntimeAvailable()).isTrue();
        assertThat(removed.getRuntimeAvailable()).isFalse();
        verify(menuRepo).save(removed);
    }

    @Test
    void preservesAParentThatPointsToASystemMenu() {
        Menu edited = pluginMenu("route:" + MODULE_NAME + ":walletTransactions", false);
        edited.setParentCode("system:dashboard");
        when(menuRepo.findByPluginCodeAndRegistrationKey(
                PLUGIN_CODE,
                "route:" + MODULE_NAME + ":walletTransactions"
        )).thenReturn(Optional.of(edited));

        service.project(PLUGIN_CODE, List.of(module(route(
                "/wallet/transactions",
                "walletTransactions",
                "交易记录",
                "/wallet",
                "钱包管理"
        ))));

        assertThat(edited.getParentCode()).isEqualTo("system:dashboard");
        assertThat(edited.getPluginCode()).isEqualTo(PLUGIN_CODE);
        assertThat(edited.getRuntimeAvailable()).isTrue();
    }

    private PluginFrontendModuleInfo module(PluginFrontendRouteInfo route) {
        return new PluginFrontendModuleInfo(
                PLUGIN_CODE,
                "/plugins/wallet/remoteEntry.js",
                MODULE_NAME,
                "1.0.0",
                "",
                "钱包中心",
                "wallet",
                20,
                List.of(route)
        );
    }

    private PluginFrontendRouteInfo route(String path,
                                          String name,
                                          String title,
                                          String parentPath,
                                          String parentTitle) {
        return new PluginFrontendRouteInfo(
                path,
                name,
                title,
                "list",
                parentPath,
                parentTitle,
                "folder",
                10,
                "wallet/transactions/index",
                "wallet:transaction:list",
                30
        );
    }

    private Menu pluginMenu(String registrationKey, boolean runtimeAvailable) {
        return Menu.builder()
                .code("plugin:" + PLUGIN_CODE + ":" + registrationKey)
                .source(MenuSource.PLUGIN)
                .pluginCode(PLUGIN_CODE)
                .pluginModuleName(MODULE_NAME)
                .pluginRegistrationKey(registrationKey)
                .runtimeAvailable(runtimeAvailable)
                .build();
    }

    private Menu menuByKey(List<Menu> menus, String registrationKey) {
        return menus.stream()
                .filter(menu -> registrationKey.equals(menu.getPluginRegistrationKey()))
                .findFirst()
                .orElseThrow();
    }

    private void assertOwnership(Menu menu, String registrationKey) {
        assertThat(menu.getSource()).isEqualTo(MenuSource.PLUGIN);
        assertThat(menu.getPluginCode()).isEqualTo(PLUGIN_CODE);
        assertThat(menu.getPluginModuleName()).isEqualTo(MODULE_NAME);
        assertThat(menu.getPluginRegistrationKey()).isEqualTo(registrationKey);
        assertThat(menu.getRuntimeAvailable()).isTrue();
    }
}
