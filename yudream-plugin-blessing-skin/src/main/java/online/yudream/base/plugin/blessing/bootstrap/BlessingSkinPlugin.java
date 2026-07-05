package online.yudream.base.plugin.blessing.bootstrap;

import online.yudream.base.plugin.blessing.application.service.BlessingSkinAppService;
import online.yudream.base.plugin.blessing.infrastructure.repository.BlessingSkinRepository;
import online.yudream.base.plugin.blessing.infrastructure.service.BlessingSkinMigrationService;
import online.yudream.base.plugin.blessing.infrastructure.service.SkinPasswordService;
import online.yudream.base.plugin.blessing.interfaces.controller.BlessingSkinAdminController;
import online.yudream.base.plugin.blessing.interfaces.controller.BlessingSkinPublicController;
import online.yudream.base.plugin.blessing.interfaces.controller.BlessingSkinUserController;
import online.yudream.base.plugin.blessing.interfaces.http.BlessingSkinHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginFrontend;
import online.yudream.base.plugin.spi.annotation.PluginPermission;
import online.yudream.base.plugin.spi.annotation.PluginPermissions;
import online.yudream.base.plugin.spi.annotation.PluginRoute;
import online.yudream.base.plugin.spi.annotation.PluginSpec;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.spi.system.skin.PluginSkinService;

@PluginSpec(
        code = BlessingSkinPlugin.CODE,
        name = "Blessing Skin 皮肤站",
        version = "1.0.0",
        description = "基于 YuDream 插件运行时实现的 Minecraft 皮肤站，支持角色、材质、衣柜、CustomSkinAPI 与 Blessing Skin 数据迁移。"
)
@PluginPermissions({
        @PluginPermission(code = BlessingSkinPlugin.VIEW_PERMISSION, name = "查看皮肤站", module = "平台插件", description = "查看皮肤站概览和公开材质"),
        @PluginPermission(code = BlessingSkinPlugin.USER_PERMISSION, name = "使用皮肤站", module = "平台插件", description = "管理自己的角色、衣柜和材质"),
        @PluginPermission(code = BlessingSkinPlugin.MANAGE_PERMISSION, name = "管理皮肤站", module = "平台插件", description = "管理皮肤站用户、全站数据和迁移任务")
})
@PluginFrontend(
        moduleName = "blessingSkin",
        menuTitle = "皮肤",
        menuIcon = "i-ri:t-shirt-2-line",
        menuSort = 19,
        routes = {
        @PluginRoute(
                path = "/platform/plugins/blessing-skin/dashboard",
                name = "platform-plugin-blessing-skin-dashboard",
                title = "仪表盘",
                icon = "i-ri:dashboard-3-line",
                component = "blessing-skin/Dashboard",
                permission = BlessingSkinPlugin.VIEW_PERMISSION,
                sort = 50
        ),
        @PluginRoute(
                path = "/platform/plugins/blessing-skin/players",
                name = "platform-plugin-blessing-skin-players",
                title = "我的角色",
                icon = "i-ri:gamepad-line",
                component = "blessing-skin/Players",
                permission = BlessingSkinPlugin.USER_PERMISSION,
                sort = 40
        ),
        @PluginRoute(
                path = "/platform/plugins/blessing-skin/textures",
                name = "platform-plugin-blessing-skin-textures",
                title = "皮肤库",
                icon = "i-ri:t-shirt-2-line",
                component = "blessing-skin/Textures",
                permission = BlessingSkinPlugin.VIEW_PERMISSION,
                sort = 30
        ),
        @PluginRoute(
                path = "/platform/plugins/blessing-skin/closet",
                name = "platform-plugin-blessing-skin-closet",
                title = "我的衣柜",
                icon = "i-ri:archive-drawer-line",
                component = "blessing-skin/Closet",
                permission = BlessingSkinPlugin.USER_PERMISSION,
                sort = 20
        ),
        @PluginRoute(
                path = "/platform/plugins/blessing-skin/system",
                name = "platform-plugin-blessing-skin-system",
                title = "皮肤站管理",
                icon = "i-ri:settings-3-line",
                component = "blessing-skin/System",
                permission = BlessingSkinPlugin.MANAGE_PERMISSION,
                sort = 10
        )
})
public class BlessingSkinPlugin implements YuDreamPlugin {

    public static final String CODE = "blessing-skin";
    public static final String VIEW_PERMISSION = "plugin:blessing-skin:view";
    public static final String USER_PERMISSION = "plugin:blessing-skin:user";
    public static final String MANAGE_PERMISSION = "plugin:blessing-skin:manage";

    @Override
    public void onEnable(PluginContext context) {
        BlessingSkinRepository repository = new BlessingSkinRepository(context.documents(), context.files());
        BlessingSkinAppService appService = new BlessingSkinAppService(
                repository,
                new SkinPasswordService(),
                new BlessingSkinMigrationService(repository)
        );
        context.registerExtension(PluginSkinService.class, appService);
        BlessingSkinHttpFacade http = new BlessingSkinHttpFacade(appService, context.framework());
        context.registerHttpController(new BlessingSkinPublicController(http));
        context.registerHttpController(new BlessingSkinUserController(http));
        context.registerHttpController(new BlessingSkinAdminController(http));
    }
}
