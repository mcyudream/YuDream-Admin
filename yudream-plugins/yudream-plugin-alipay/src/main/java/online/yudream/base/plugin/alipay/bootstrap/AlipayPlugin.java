package online.yudream.base.plugin.alipay.bootstrap;

import online.yudream.base.plugin.alipay.application.service.AlipayAppService;
import online.yudream.base.plugin.alipay.application.service.AlipayPaymentChannel;
import online.yudream.base.plugin.alipay.infrastructure.repository.AlipayRepository;
import online.yudream.base.plugin.alipay.infrastructure.service.AlipayGatewayService;
import online.yudream.base.plugin.alipay.interfaces.controller.AlipayController;
import online.yudream.base.plugin.alipay.interfaces.http.AlipayHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginFrontend;
import online.yudream.base.plugin.spi.annotation.PluginPermission;
import online.yudream.base.plugin.spi.annotation.PluginPermissions;
import online.yudream.base.plugin.spi.annotation.PluginRoute;
import online.yudream.base.plugin.spi.annotation.PluginSpec;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.spi.system.payment.PluginPaymentChannel;
import online.yudream.base.plugin.spi.system.wallet.PluginWalletService;

@PluginSpec(
        code = AlipayPlugin.CODE,
        name = "yudream-alipay",
        version = "1.0.0",
        description = "接入支付宝官方收款 API，创建充值订单并通过钱包插件完成余额入账。",
        dependencies = {"yudream-wallet"}
)
@PluginPermissions({
        @PluginPermission(code = AlipayPlugin.USER_PERMISSION, name = "使用支付宝充值", module = "平台插件", description = "创建支付宝充值订单并查询自己的订单"),
        @PluginPermission(code = AlipayPlugin.MANAGE_PERMISSION, name = "管理支付宝收款", module = "平台插件", description = "管理支付宝配置、订单和通知调试")
})
@PluginFrontend(
        moduleName = "yudreamAlipay",
        menuTitle = "钱包",
        menuIcon = "i-ri:wallet-3-line",
        menuSort = 30,
        routes = {
                @PluginRoute(
                        path = "/platform/plugins/yudream-alipay/system/settings",
                        name = "platform-plugin-yudream-alipay-settings",
                        title = "支付宝配置",
                        icon = "i-ri:alipay-line",
                        parentPath = "/platform/plugins/yudream-wallet/system",
                        parentTitle = "钱包管理",
                        parentIcon = "i-ri:settings-3-line",
                        parentSort = 10,
                        component = "yudream-alipay/Settings",
                        permission = AlipayPlugin.MANAGE_PERMISSION,
                        sort = 10
                )
        }
)
public class AlipayPlugin implements YuDreamPlugin {

    public static final String CODE = "yudream-alipay";
    public static final String USER_PERMISSION = "plugin:yudream-alipay:user";
    public static final String MANAGE_PERMISSION = "plugin:yudream-alipay:manage";

    @Override
    public void onEnable(PluginContext context) {
        PluginWalletService walletService = context.framework()
                .extension("yudream-wallet", PluginWalletService.class)
                .orElseThrow(() -> new IllegalStateException("钱包插件未启用或未注册钱包服务"));
        AlipayAppService appService = new AlipayAppService(
                new AlipayRepository(context.documents()),
                new AlipayGatewayService(),
                walletService,
                context.framework()
        );
        context.registerExtension(PluginPaymentChannel.class, new AlipayPaymentChannel(appService));
        context.registerHttpController(new AlipayController(new AlipayHttpFacade(appService)));
    }
}
