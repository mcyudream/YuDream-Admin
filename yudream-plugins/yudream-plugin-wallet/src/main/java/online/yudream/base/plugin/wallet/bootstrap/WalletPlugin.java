package online.yudream.base.plugin.wallet.bootstrap;

import online.yudream.base.plugin.spi.annotation.PluginDashboardCard;
import online.yudream.base.plugin.spi.annotation.PluginFrontend;
import online.yudream.base.plugin.spi.annotation.PluginPermission;
import online.yudream.base.plugin.spi.annotation.PluginPermissions;
import online.yudream.base.plugin.spi.annotation.PluginRoute;
import online.yudream.base.plugin.spi.annotation.PluginSpec;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.spi.system.wallet.PluginWalletService;
import online.yudream.base.plugin.wallet.application.service.WalletAppService;
import online.yudream.base.plugin.wallet.infrastructure.repository.WalletRepository;
import online.yudream.base.plugin.wallet.interfaces.controller.WalletController;
import online.yudream.base.plugin.wallet.interfaces.http.WalletHttpFacade;

@PluginSpec(
        code = WalletPlugin.CODE,
        name = "yudream-wallet",
        version = "1.0.0",
        description = "提供人民币余额和可扩展积分资产的钱包能力，支持余额增减、转账和流水幂等。"
)
@PluginPermissions({
        @PluginPermission(code = WalletPlugin.VIEW_PERMISSION, name = "查看钱包", module = "平台插件", description = "查看钱包资产类型和基础状态"),
        @PluginPermission(code = WalletPlugin.USER_PERMISSION, name = "使用钱包", module = "平台插件", description = "查看个人余额并发起转账"),
        @PluginPermission(code = WalletPlugin.MANAGE_PERMISSION, name = "管理钱包", module = "平台插件", description = "管理资产、余额入扣账和流水")
})
@PluginDashboardCard(
        code = "wallet-balance",
        title = "我的钱包",
        description = "展示人民币余额和积分资产概览。",
        icon = "i-ri:wallet-3-line",
        category = "钱包",
        permission = WalletPlugin.USER_PERMISSION,
        component = "yudream-wallet/DashboardBalanceCard",
        actionPath = "/platform/plugins/yudream-wallet",
        tone = "green",
        defaultW = 4,
        defaultH = 2,
        minW = 3,
        minH = 2,
        sort = 40
)
@PluginFrontend(
        moduleName = "yudreamWallet",
        menuTitle = "钱包",
        menuIcon = "i-ri:wallet-3-line",
        menuSort = 30,
        routes = {
                @PluginRoute(
                        path = "/platform/plugins/yudream-wallet",
                        name = "platform-plugin-yudream-wallet",
                        title = "我的钱包",
                        icon = "i-ri:wallet-3-line",
                        component = "yudream-wallet/Home",
                        permission = WalletPlugin.USER_PERMISSION,
                        sort = 20
                ),
                @PluginRoute(
                        path = "/platform/plugins/yudream-wallet/recharge",
                        name = "platform-plugin-yudream-wallet-recharge",
                        title = "钱包充值",
                        icon = "i-ri:bank-card-line",
                        component = "yudream-wallet/Recharge",
                        permission = WalletPlugin.USER_PERMISSION,
                        sort = 25
                ),
                @PluginRoute(
                        path = "/platform/plugins/yudream-wallet/system/settings",
                        name = "platform-plugin-yudream-wallet-settings",
                        title = "钱包设置",
                        icon = "i-ri:settings-3-line",
                        parentPath = "/platform/plugins/yudream-wallet/system",
                        parentTitle = "钱包管理",
                        parentIcon = "i-ri:settings-3-line",
                        parentSort = 10,
                        component = "yudream-wallet/Settings",
                        permission = WalletPlugin.MANAGE_PERMISSION,
                        sort = 30
                ),
                @PluginRoute(
                        path = "/platform/plugins/yudream-wallet/system/balances",
                        name = "platform-plugin-yudream-wallet-balances",
                        title = "余额管理",
                        icon = "i-ri:database-2-line",
                        parentPath = "/platform/plugins/yudream-wallet/system",
                        parentTitle = "钱包管理",
                        parentIcon = "i-ri:settings-3-line",
                        parentSort = 10,
                        component = "yudream-wallet/Balances",
                        permission = WalletPlugin.MANAGE_PERMISSION,
                        sort = 20
                ),
                @PluginRoute(
                        path = "/platform/plugins/yudream-wallet/system/transactions",
                        name = "platform-plugin-yudream-wallet-transactions",
                        title = "系统流水",
                        icon = "i-ri:file-list-3-line",
                        parentPath = "/platform/plugins/yudream-wallet/system",
                        parentTitle = "钱包管理",
                        parentIcon = "i-ri:settings-3-line",
                        parentSort = 10,
                        component = "yudream-wallet/Transactions",
                        permission = WalletPlugin.MANAGE_PERMISSION,
                        sort = 10
                )
        }
)
public class WalletPlugin implements YuDreamPlugin {

    public static final String CODE = "yudream-wallet";
    public static final String VIEW_PERMISSION = "plugin:yudream-wallet:view";
    public static final String USER_PERMISSION = "plugin:yudream-wallet:user";
    public static final String MANAGE_PERMISSION = "plugin:yudream-wallet:manage";

    @Override
    public void onEnable(PluginContext context) {
        WalletAppService appService = new WalletAppService(new WalletRepository(context.documents()), context.framework());
        appService.initializeDefaults();
        context.registerExtension(PluginWalletService.class, appService);
        context.registerHttpController(new WalletController(new WalletHttpFacade(appService, context.framework())));
    }
}
