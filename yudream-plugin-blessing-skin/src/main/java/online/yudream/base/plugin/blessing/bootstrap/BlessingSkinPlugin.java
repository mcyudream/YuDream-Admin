package online.yudream.base.plugin.blessing.bootstrap;

import online.yudream.base.plugin.blessing.application.service.BlessingSkinAppService;
import online.yudream.base.plugin.blessing.infrastructure.repository.BlessingSkinRepository;
import online.yudream.base.plugin.blessing.infrastructure.service.BlessingSkinMigrationService;
import online.yudream.base.plugin.blessing.infrastructure.service.SkinPasswordService;
import online.yudream.base.plugin.blessing.interfaces.http.BlessingSkinHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginFrontend;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.annotation.PluginPermission;
import online.yudream.base.plugin.spi.annotation.PluginPermissions;
import online.yudream.base.plugin.spi.annotation.PluginRoute;
import online.yudream.base.plugin.spi.annotation.PluginSpec;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.spi.system.skin.PluginSkinService;

@PluginSpec(
        code = BlessingSkinPlugin.CODE,
        name = "Blessing Skin 皮肤站",
        version = "1.0.0",
        description = "基于 YuDream 插件运行时实现的 Minecraft 皮肤站，支持角色、材质、CustomSkinAPI 与 Blessing Skin 数据迁移。"
)
@PluginPermissions({
        @PluginPermission(code = BlessingSkinPlugin.VIEW_PERMISSION, name = "查看皮肤站", module = "平台插件", description = "查看皮肤站用户、角色和材质"),
        @PluginPermission(code = BlessingSkinPlugin.MANAGE_PERMISSION, name = "管理皮肤站", module = "平台插件", description = "管理皮肤站用户、角色、材质和迁移任务")
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
                title = "角色管理",
                icon = "i-ri:gamepad-line",
                component = "blessing-skin/Players",
                permission = BlessingSkinPlugin.VIEW_PERMISSION,
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
                title = "衣柜管理",
                icon = "i-ri:archive-drawer-line",
                component = "blessing-skin/Closet",
                permission = BlessingSkinPlugin.VIEW_PERMISSION,
                sort = 20
        ),
        @PluginRoute(
                path = "/platform/plugins/blessing-skin/system",
                name = "platform-plugin-blessing-skin-system",
                title = "系统管理",
                icon = "i-ri:settings-3-line",
                component = "blessing-skin/System",
                permission = BlessingSkinPlugin.MANAGE_PERMISSION,
                sort = 10
        )
})
public class BlessingSkinPlugin implements YuDreamPlugin {

    public static final String CODE = "blessing-skin";
    public static final String VIEW_PERMISSION = "plugin:blessing-skin:view";
    public static final String MANAGE_PERMISSION = "plugin:blessing-skin:manage";

    private BlessingSkinHttpFacade http;

    @Override
    public void onEnable(PluginContext context) {
        BlessingSkinRepository repository = new BlessingSkinRepository(context.documents(), context.files());
        BlessingSkinAppService appService = new BlessingSkinAppService(
                repository,
                new SkinPasswordService(),
                new BlessingSkinMigrationService(repository)
        );
        context.registerExtension(PluginSkinService.class, appService);
        this.http = new BlessingSkinHttpFacade(appService);
    }

    @Override
    public void onDisable(PluginContext context) {
        this.http = null;
    }

    @PluginHttpEndpoint(method = "GET", path = "/status", permission = VIEW_PERMISSION)
    public PluginHttpResponse status() {
        return http.status();
    }

    @PluginHttpEndpoint(method = "GET", path = "/users", permission = VIEW_PERMISSION)
    public PluginHttpResponse users(PluginHttpRequest request) {
        return http.users(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/users", permission = MANAGE_PERMISSION)
    public PluginHttpResponse createUser(PluginHttpRequest request) {
        return http.createUser(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/players", permission = VIEW_PERMISSION)
    public PluginHttpResponse players(PluginHttpRequest request) {
        return http.players(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/players", permission = MANAGE_PERMISSION)
    public PluginHttpResponse createPlayer(PluginHttpRequest request) {
        return http.createPlayer(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/players/{name}", permission = VIEW_PERMISSION)
    public PluginHttpResponse player(PluginHttpRequest request) {
        return http.player(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/players/{name}/textures", permission = MANAGE_PERMISSION)
    public PluginHttpResponse assignTextures(PluginHttpRequest request) {
        return http.assignTextures(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/textures", permission = VIEW_PERMISSION)
    public PluginHttpResponse textures(PluginHttpRequest request) {
        return http.textures(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/textures", permission = MANAGE_PERMISSION)
    public PluginHttpResponse uploadTexture(PluginHttpRequest request) {
        return http.uploadTexture(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/textures/{hash}", wrapResult = false)
    public PluginHttpResponse textureContent(PluginHttpRequest request) {
        return http.textureContent(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/closet", permission = VIEW_PERMISSION)
    public PluginHttpResponse closet(PluginHttpRequest request) {
        return http.closet(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/closet", permission = MANAGE_PERMISSION)
    public PluginHttpResponse saveClosetItem(PluginHttpRequest request) {
        return http.saveClosetItem(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/closet/{id}", permission = MANAGE_PERMISSION)
    public PluginHttpResponse deleteClosetItem(PluginHttpRequest request) {
        return http.deleteClosetItem(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/settings", permission = VIEW_PERMISSION)
    public PluginHttpResponse settings() {
        return http.settings();
    }

    @PluginHttpEndpoint(method = "PUT", path = "/settings", permission = MANAGE_PERMISSION)
    public PluginHttpResponse saveSettings(PluginHttpRequest request) {
        return http.saveSettings(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/csl/{name}", wrapResult = false)
    public PluginHttpResponse customSkinProfile(PluginHttpRequest request) {
        return http.customSkinProfile(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/migration/blessing-skin", permission = MANAGE_PERMISSION)
    public PluginHttpResponse migrate(PluginHttpRequest request) {
        return http.migrate(request);
    }
}
