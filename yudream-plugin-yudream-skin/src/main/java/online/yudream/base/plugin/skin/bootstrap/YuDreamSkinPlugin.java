package online.yudream.base.plugin.skin.bootstrap;

import online.yudream.base.plugin.skin.application.service.YuDreamSkinAppService;
import online.yudream.base.plugin.skin.infrastructure.repository.YuDreamSkinRepository;
import online.yudream.base.plugin.skin.infrastructure.service.YuDreamSkinMigrationService;
import online.yudream.base.plugin.skin.interfaces.controller.YuDreamSkinAdminController;
import online.yudream.base.plugin.skin.interfaces.controller.YuDreamSkinPublicController;
import online.yudream.base.plugin.skin.interfaces.controller.YuDreamSkinUserController;
import online.yudream.base.plugin.skin.interfaces.http.YuDreamSkinHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginDashboardCard;
import online.yudream.base.plugin.spi.annotation.PluginFrontend;
import online.yudream.base.plugin.spi.annotation.PluginPermission;
import online.yudream.base.plugin.spi.annotation.PluginPermissions;
import online.yudream.base.plugin.spi.annotation.PluginRoute;
import online.yudream.base.plugin.spi.annotation.PluginSpec;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.spi.system.skin.PluginSkinService;

@PluginSpec(
        code = YuDreamSkinPlugin.CODE,
        name = "yudream-skin",
        version = "1.0.0",
        description = "基于 YuDream 插件运行时实现的 Minecraft 皮肤站，支持角色、材质、衣柜、CustomSkinAPI 与 Blessing Skin 数据迁移。"
)
@PluginPermissions({
        @PluginPermission(code = YuDreamSkinPlugin.VIEW_PERMISSION, name = "查看皮肤站", module = "平台插件", description = "查看皮肤站概览和公开材质"),
        @PluginPermission(code = YuDreamSkinPlugin.USER_PERMISSION, name = "使用皮肤站", module = "平台插件", description = "管理自己的角色、衣柜和材质"),
        @PluginPermission(code = YuDreamSkinPlugin.MANAGE_PERMISSION, name = "管理皮肤站", module = "平台插件", description = "管理皮肤插件设置、角色、材质和迁移任务")
})
@PluginDashboardCard(
        code = "current-player",
        title = "当前角色",
        description = "展示默认角色及已绑定的皮肤、披风信息。",
        icon = "i-ri:gamepad-line",
        category = "皮肤站",
        permission = YuDreamSkinPlugin.USER_PERMISSION,
        component = "yudream-skin/DashboardCurrentPlayerCard",
        actionPath = "/platform/plugins/yudream-skin/players",
        tone = "blue",
        defaultW = 4,
        defaultH = 3,
        minW = 3,
        minH = 2,
        sort = 10
)
@PluginDashboardCard(
        code = "skin-preview",
        title = "皮肤预览",
        description = "预览当前选中角色的皮肤与披风。",
        icon = "i-ri:t-shirt-2-line",
        category = "皮肤站",
        permission = YuDreamSkinPlugin.USER_PERMISSION,
        component = "yudream-skin/DashboardSkinPreviewCard",
        actionPath = "/platform/plugins/yudream-skin/players",
        tone = "cyan",
        defaultW = 4,
        defaultH = 4,
        minW = 3,
        minH = 3,
        sort = 20
)
@PluginDashboardCard(
        code = "skin-stats",
        title = "皮肤站统计",
        description = "展示皮肤站用户数、角色数、材质数与衣柜项数量。",
        icon = "i-ri:bar-chart-box-line",
        category = "皮肤站",
        permission = YuDreamSkinPlugin.VIEW_PERMISSION,
        component = "yudream-skin/DashboardStatsCard",
        actionPath = "/platform/plugins/yudream-skin/dashboard",
        tone = "purple",
        defaultW = 4,
        defaultH = 3,
        minW = 3,
        minH = 2,
        sort = 30
)
@PluginFrontend(
        moduleName = "yudreamSkin",
        menuTitle = "皮肤",
        menuIcon = "i-ri:t-shirt-2-line",
        menuSort = 19,
        routes = {
                @PluginRoute(
                        path = "/platform/plugins/yudream-skin/dashboard",
                        name = "platform-plugin-yudream-skin-dashboard",
                        title = "仪表盘",
                        icon = "i-ri:dashboard-3-line",
                        component = "yudream-skin/Dashboard",
                        permission = YuDreamSkinPlugin.VIEW_PERMISSION,
                        sort = 70
                ),
                @PluginRoute(
                        path = "/platform/plugins/yudream-skin/players",
                        name = "platform-plugin-yudream-skin-players",
                        title = "我的角色",
                        icon = "i-ri:gamepad-line",
                        component = "yudream-skin/Players",
                        permission = YuDreamSkinPlugin.USER_PERMISSION,
                        sort = 60
                ),
                @PluginRoute(
                        path = "/platform/plugins/yudream-skin/textures",
                        name = "platform-plugin-yudream-skin-textures",
                        title = "皮肤库",
                        icon = "i-ri:t-shirt-2-line",
                        component = "yudream-skin/Textures",
                        permission = YuDreamSkinPlugin.VIEW_PERMISSION,
                        sort = 50
                ),
                @PluginRoute(
                        path = "/platform/plugins/yudream-skin/closet",
                        name = "platform-plugin-yudream-skin-closet",
                        title = "我的衣柜",
                        icon = "i-ri:archive-drawer-line",
                        component = "yudream-skin/Closet",
                        permission = YuDreamSkinPlugin.USER_PERMISSION,
                        sort = 40
                ),
                @PluginRoute(
                        path = "/platform/plugins/yudream-skin/system/settings",
                        name = "platform-plugin-yudream-skin-settings",
                        title = "插件设置",
                        icon = "i-ri:settings-3-line",
                        parentPath = "/platform/plugins/yudream-skin/system",
                        parentTitle = "皮肤站管理",
                        parentIcon = "i-ri:settings-3-line",
                        parentSort = 10,
                        component = "yudream-skin/Settings",
                        permission = YuDreamSkinPlugin.MANAGE_PERMISSION,
                        sort = 30
                ),
                @PluginRoute(
                        path = "/platform/plugins/yudream-skin/system/players",
                        name = "platform-plugin-yudream-skin-manage-players",
                        title = "角色管理",
                        icon = "i-ri:gamepad-line",
                        parentPath = "/platform/plugins/yudream-skin/system",
                        parentTitle = "皮肤站管理",
                        parentIcon = "i-ri:settings-3-line",
                        parentSort = 10,
                        component = "yudream-skin/PlayerManagement",
                        permission = YuDreamSkinPlugin.MANAGE_PERMISSION,
                        sort = 20
                ),
                @PluginRoute(
                        path = "/platform/plugins/yudream-skin/system/textures",
                        name = "platform-plugin-yudream-skin-manage-textures",
                        title = "材质管理",
                        icon = "i-ri:t-shirt-2-line",
                        parentPath = "/platform/plugins/yudream-skin/system",
                        parentTitle = "皮肤站管理",
                        parentIcon = "i-ri:settings-3-line",
                        parentSort = 10,
                        component = "yudream-skin/TextureManagement",
                        permission = YuDreamSkinPlugin.MANAGE_PERMISSION,
                        sort = 10
                )
        }
)
public class YuDreamSkinPlugin implements YuDreamPlugin {

    public static final String CODE = "yudream-skin";
    public static final String VIEW_PERMISSION = "plugin:yudream-skin:view";
    public static final String USER_PERMISSION = "plugin:yudream-skin:user";
    public static final String MANAGE_PERMISSION = "plugin:yudream-skin:manage";

    @Override
    public void onEnable(PluginContext context) {
        YuDreamSkinRepository repository = new YuDreamSkinRepository(context.documents(), context.files());
        YuDreamSkinAppService appService = new YuDreamSkinAppService(
                repository,
                new YuDreamSkinMigrationService(repository, context.framework().users())
        );
        context.registerExtension(PluginSkinService.class, appService);
        YuDreamSkinHttpFacade http = new YuDreamSkinHttpFacade(appService, context.framework());
        context.registerHttpController(new YuDreamSkinPublicController(http));
        context.registerHttpController(new YuDreamSkinUserController(http));
        context.registerHttpController(new YuDreamSkinAdminController(http));
    }
}
