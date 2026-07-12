package online.yudream.base.application.platform.plugin;

import online.yudream.base.application.platform.plugin.service.PluginMenuProjectionService;
import online.yudream.base.domain.common.exception.BizException;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    }

    private void stubDefaultRepo() {
        when(menuRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsModuleParentAndRouteMenusFromFrontendDeclaration() {
        stubDefaultRepo();
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
        assertThat(parent.getComponent()).isEqualTo("Layout");
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
    void createsContextualRoutesAsHiddenMenuEntries() {
        stubDefaultRepo();
        PluginFrontendRouteInfo contextualRoute = new PluginFrontendRouteInfo(
                "/wallet/transactions/detail",
                "walletTransactionDetail",
                "交易详情",
                "file-info-line",
                "/wallet",
                "钱包管理",
                "folder",
                10,
                "wallet/transactions/detail",
                "wallet:transaction:list",
                30,
                true
        );

        service.project(PLUGIN_CODE, List.of(module(contextualRoute)));

        ArgumentCaptor<Menu> captor = ArgumentCaptor.forClass(Menu.class);
        verify(menuRepo, org.mockito.Mockito.times(3)).save(captor.capture());
        Menu route = menuByKey(captor.getAllValues(), "route:" + MODULE_NAME + ":walletTransactionDetail");
        assertThat(route.getPath()).isEqualTo("/wallet/transactions/detail");
        assertThat(route.getComponent()).isEqualTo("wallet/transactions/detail");
        assertThat(route.getVisible()).isFalse();
    }

    @Test
    void existingMenuCodeIsNeverOverwrittenByADeclaration() {
        FailingMenuRepo repo = new FailingMenuRepo();
        PluginMenuProjectionService projectionService = new PluginMenuProjectionService(repo);
        repo.put(pluginMenu("module:" + MODULE_NAME, true));
        Menu layout = pluginMenu("parent:" + MODULE_NAME + ":/wallet", true);
        layout.setType(MenuNodeType.LAYOUT);
        layout.setComponent("CustomLayout");
        repo.put(layout);
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
        repo.put(edited);

        projectionService.restoreAvailable(PLUGIN_CODE);
        projectionService.project(PLUGIN_CODE, List.of(module(route(
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
        assertThat(edited.getPluginCode()).isEqualTo("old-plugin-code");
        assertThat(edited.getPluginModuleName()).isEqualTo("old-module");
        assertThat(edited.getRuntimeAvailable()).isFalse();
        assertThat(repo.findByCode(edited.getCode())).containsSame(edited);
        assertThat(repo.getSaveCount()).isZero();
    }

    @Test
    void removedDeclarationHidesPersistedMenuFromRuntimeNavigation() {
        FailingMenuRepo repo = new FailingMenuRepo();
        PluginMenuProjectionService projectionService = new PluginMenuProjectionService(repo);
        repo.put(pluginMenu("module:" + MODULE_NAME, true));
        Menu current = pluginMenu("route:" + MODULE_NAME + ":walletTransactions", true);
        Menu removed = pluginMenu("route:" + MODULE_NAME + ":walletReports", true);
        repo.put(current);
        repo.put(removed);

        projectionService.project(PLUGIN_CODE, List.of(module(route(
                "/wallet/transactions",
                "walletTransactions",
                "交易记录",
                null,
                null
        ))));

        assertThat(current.getRuntimeAvailable()).isTrue();
        assertThat(removed.getRuntimeAvailable()).isFalse();
        assertThat(repo.getSaveCount()).isEqualTo(1);
    }

    @Test
    void enablingRestoresAllHistoricalMenusOwnedByExactPluginCode() {
        FailingMenuRepo repo = new FailingMenuRepo();
        PluginMenuProjectionService projectionService = new PluginMenuProjectionService(repo);
        repo.put(pluginMenu("module:" + MODULE_NAME, true));
        repo.put(pluginMenu("route:" + MODULE_NAME + ":walletTransactions", true));
        Menu historical = pluginMenu("route:" + MODULE_NAME + ":walletReports", false);
        historical.setParentCode("system:dashboard");
        historical.setComponent("custom/reports");
        repo.put(historical);

        projectionService.restoreAvailable(PLUGIN_CODE);

        assertThat(historical.getRuntimeAvailable()).isTrue();
        assertThat(historical.getParentCode()).isEqualTo("system:dashboard");
        assertThat(historical.getComponent()).isEqualTo("custom/reports");
        assertThat(repo.getSaveCount()).isEqualTo(1);
    }

    @Test
    void disablingPluginOnlyHidesMenusWithExactPluginCodeAndDoesNotRecurse() {
        FailingMenuRepo repo = new FailingMenuRepo();
        PluginMenuProjectionService projectionService = new PluginMenuProjectionService(repo);
        Menu ownerDirectory = pluginMenu("module:" + MODULE_NAME, true);
        repo.put(ownerDirectory);
        Menu foreignChild = Menu.builder()
                .code("plugin:yudream-alipay:route:alipayAdmin:settings")
                .source(MenuSource.PLUGIN)
                .pluginCode("yudream-alipay")
                .parentCode(ownerDirectory.getCode())
                .runtimeAvailable(true)
                .build();
        repo.put(foreignChild);

        projectionService.markUnavailable(PLUGIN_CODE);

        assertThat(ownerDirectory.getRuntimeAvailable()).isFalse();
        assertThat(foreignChild.getRuntimeAvailable()).isTrue();
        assertThat(repo.getSaveCount()).isEqualTo(1);
    }

    @Test
    void sharedPluginParentDoesNotCreateAnotherModuleMenu() {
        String sharedParentCode = "plugin:yudream-wallet:module:yudreamWallet";
        FailingMenuRepo repo = new FailingMenuRepo();
        Menu sharedParent = Menu.builder()
                .code(sharedParentCode)
                .source(MenuSource.PLUGIN)
                .pluginCode("yudream-wallet")
                .runtimeAvailable(true)
                .build();
        repo.put(sharedParent);
        PluginFrontendModuleInfo consumer = new PluginFrontendModuleInfo(
                "yudream-alipay",
                "/plugins/alipay/remoteEntry.js",
                "yudreamAlipay",
                "1.0.0",
                "",
                "钱包中心",
                "wallet",
                20,
                List.of(route(
                        "/wallet/alipay",
                        "alipaySettings",
                        "支付宝配置",
                        null,
                        null
                )),
                sharedParentCode,
                true,
                MenuStatus.ACTIVE
        );

        new PluginMenuProjectionService(repo).project("yudream-alipay", List.of(consumer));

        assertThat(repo.findByCode("plugin:yudream-alipay:module:yudreamAlipay")).isEmpty();
        assertThat(repo.findByCode("plugin:yudream-alipay:route:yudreamAlipay:alipaySettings"))
                .get()
                .extracting(Menu::getParentCode)
                .isEqualTo(sharedParentCode);
        assertThat(repo.findAll()).hasSize(2);
    }

    @Test
    void preservesAParentThatPointsToASystemMenu() {
        FailingMenuRepo repo = new FailingMenuRepo();
        Menu edited = pluginMenu("route:" + MODULE_NAME + ":walletTransactions", false);
        edited.setParentCode("system:dashboard");
        repo.put(edited);

        PluginMenuProjectionService projectionService = new PluginMenuProjectionService(repo);
        projectionService.restoreAvailable(PLUGIN_CODE);
        projectionService.project(PLUGIN_CODE, List.of(module(route(
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

    @Test
    void rejectsConflictingParentDeclarationsBeforeAnyWrite() {
        PluginFrontendRouteInfo first = route(
                "/wallet/transactions",
                "walletTransactions",
                "交易记录",
                " /wallet ",
                "钱包管理"
        );
        PluginFrontendRouteInfo conflicting = new PluginFrontendRouteInfo(
                "/wallet/reports",
                "walletReports",
                "钱包报表",
                "list",
                "/wallet",
                "其他目录",
                "folder",
                10,
                "wallet/reports/index",
                "wallet:report:list",
                40
        );

        assertThatThrownBy(() -> service.project(
                PLUGIN_CODE,
                List.of(module(List.of(conflicting, first)))
        )).isInstanceOf(BizException.class)
                .hasMessageContaining("父目录声明冲突");

        verifyNoInteractions(menuRepo);
    }

    @Test
    void rejectsDuplicateRouteRegistrationBeforeAnyWrite() {
        PluginFrontendRouteInfo duplicate = route(
                "/wallet/reports",
                "walletTransactions",
                "钱包报表",
                null,
                null
        );

        assertThatThrownBy(() -> service.project(
                PLUGIN_CODE,
                List.of(module(List.of(
                        route("/wallet/transactions", "walletTransactions", "交易记录", null, null),
                        duplicate
                )))
        )).isInstanceOf(BizException.class)
                .hasMessageContaining("注册标识重复");

        verifyNoInteractions(menuRepo);
    }

    @Test
    void retryAfterMidReconciliationFailureConvergesToTheCompleteProjection() {
        FailingMenuRepo repo = new FailingMenuRepo();
        Menu removed = pluginMenu("route:" + MODULE_NAME + ":removed", true);
        repo.put(removed);
        repo.failOnceOnSave(2);
        PluginMenuProjectionService retryableService = new PluginMenuProjectionService(repo);
        PluginFrontendModuleInfo declaration = module(route(
                "/wallet/transactions",
                "walletTransactions",
                "交易记录",
                null,
                null
        ));

        assertThatThrownBy(() -> retryableService.project(PLUGIN_CODE, List.of(declaration)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repo.findByPluginCodeAndRegistrationKey(
                PLUGIN_CODE,
                "module:" + MODULE_NAME
        )).isPresent();
        assertThat(repo.findByPluginCodeAndRegistrationKey(
                PLUGIN_CODE,
                "route:" + MODULE_NAME + ":walletTransactions"
        )).isEmpty();
        assertThat(removed.getRuntimeAvailable()).isTrue();

        retryableService.project(PLUGIN_CODE, List.of(declaration));

        assertThat(repo.findByPluginCode(PLUGIN_CODE)).hasSize(3);
        assertThat(repo.findByPluginCodeAndRegistrationKey(
                PLUGIN_CODE,
                "module:" + MODULE_NAME
        )).get().extracting(Menu::getRuntimeAvailable).isEqualTo(true);
        assertThat(repo.findByPluginCodeAndRegistrationKey(
                PLUGIN_CODE,
                "route:" + MODULE_NAME + ":walletTransactions"
        )).get().extracting(Menu::getRuntimeAvailable).isEqualTo(true);
        assertThat(repo.findByPluginCodeAndRegistrationKey(
                PLUGIN_CODE,
                "route:" + MODULE_NAME + ":removed"
        )).get().extracting(Menu::getRuntimeAvailable).isEqualTo(false);
    }

    @Test
    void concurrentInsertOfTheSameMenuCodeConvergesWithoutFailingEnable() {
        String moduleCode = "plugin:" + PLUGIN_CODE + ":module:" + MODULE_NAME;
        Menu winner = pluginMenu("module:" + MODULE_NAME, true);
        when(menuRepo.findByCode(moduleCode))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(menuRepo.save(any())).thenThrow(new IllegalStateException("duplicate key"));

        assertThatCode(() -> service.project(PLUGIN_CODE, List.of(module(List.of()))))
                .doesNotThrowAnyException();

        verify(menuRepo).save(any(Menu.class));
    }

    private PluginFrontendModuleInfo module(PluginFrontendRouteInfo route) {
        return module(List.of(route));
    }

    private PluginFrontendModuleInfo module(List<PluginFrontendRouteInfo> routes) {
        return new PluginFrontendModuleInfo(
                PLUGIN_CODE,
                "/plugins/wallet/remoteEntry.js",
                MODULE_NAME,
                "1.0.0",
                "",
                "钱包中心",
                "wallet",
                20,
                routes
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

    private static class FailingMenuRepo implements MenuRepo {

        private final Map<String, Menu> menus = new LinkedHashMap<>();
        private int saveCount;
        private int failingSave = -1;

        void put(Menu menu) {
            menus.put(menu.getCode(), menu);
        }

        void failOnceOnSave(int saveNumber) {
            failingSave = saveNumber;
        }

        int getSaveCount() {
            return saveCount;
        }

        @Override
        public Menu save(Menu menu) {
            saveCount++;
            if (saveCount == failingSave) {
                failingSave = -1;
                throw new IllegalStateException("simulated persistence failure");
            }
            menus.put(menu.getCode(), menu);
            return menu;
        }

        @Override
        public Optional<Menu> findByCode(String code) {
            return Optional.ofNullable(menus.get(code));
        }

        @Override
        public Optional<Menu> findByPluginCodeAndRegistrationKey(String pluginCode, String registrationKey) {
            return findByPluginCode(pluginCode).stream()
                    .filter(menu -> registrationKey.equals(menu.getPluginRegistrationKey()))
                    .findFirst();
        }

        @Override
        public List<Menu> findAll() {
            return List.copyOf(menus.values());
        }

        @Override
        public List<Menu> findByPluginCode(String pluginCode) {
            return menus.values().stream()
                    .filter(Menu::isPluginMenu)
                    .filter(menu -> pluginCode.equals(menu.getPluginCode()))
                    .toList();
        }

        @Override
        public List<Menu> findByTypeIn(List<MenuNodeType> types) {
            return menus.values().stream()
                    .filter(menu -> types.contains(menu.getType()))
                    .toList();
        }

        @Override
        public boolean existsByCode(String code) {
            return menus.containsKey(code);
        }

        @Override
        public long count() {
            return menus.size();
        }
    }
}
