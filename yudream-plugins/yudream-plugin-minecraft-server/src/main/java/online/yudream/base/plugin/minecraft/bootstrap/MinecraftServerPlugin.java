package online.yudream.base.plugin.minecraft.bootstrap;

import online.yudream.base.plugin.minecraft.application.service.MinecraftServerAppService;
import online.yudream.base.plugin.minecraft.infrastructure.repository.MinecraftServerDocumentRepository;
import online.yudream.base.plugin.minecraft.infrastructure.service.MinecraftStatusScheduler;
import online.yudream.base.plugin.minecraft.infrastructure.service.MinecraftStatusService;
import online.yudream.base.plugin.minecraft.interfaces.controller.MinecraftServerController;
import online.yudream.base.plugin.minecraft.interfaces.http.MinecraftServerHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginFrontend;
import online.yudream.base.plugin.spi.annotation.PluginPermission;
import online.yudream.base.plugin.spi.annotation.PluginPermissions;
import online.yudream.base.plugin.spi.annotation.PluginRoute;
import online.yudream.base.plugin.spi.annotation.PluginSpec;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.spi.system.minecraft.PluginMinecraftService;

@PluginSpec(
        code = MinecraftServerPlugin.CODE,
        name = "minecraft-server",
        version = "1.0.0",
        description = "管理 Minecraft 服务器列表、多线地址、在线状态与周目展示。"
)
@PluginPermissions({
        @PluginPermission(code = MinecraftServerPlugin.VIEW_PERMISSION, name = "查看 MC 服务器", module = "平台插件", description = "查看 Minecraft 服务器列表、详情与在线状态"),
        @PluginPermission(code = MinecraftServerPlugin.MANAGE_PERMISSION, name = "管理 MC 服务器", module = "平台插件", description = "维护 Minecraft 服务器、线路地址和周目信息"),
        @PluginPermission(code = MinecraftServerPlugin.REPORT_PERMISSION, name = "上报 MC 玩家事件", module = "平台插件", description = "Minecraft 服务器插件上报玩家加入、退出和挂机事件")
})
@PluginFrontend(
        moduleName = "minecraftServer",
        menuTitle = "MC 服务器",
        menuIcon = "i-ri:gamepad-line",
        menuSort = 45,
        routes = {
                @PluginRoute(
                        path = "/platform/plugins/minecraft-server",
                        name = "platform-plugin-minecraft-server-list",
                        title = "服务器列表",
                        icon = "i-ri:server-line",
                        component = "minecraft-server/List",
                        permission = MinecraftServerPlugin.VIEW_PERMISSION,
                        sort = 10
                ),
                @PluginRoute(
                        path = "/platform/plugins/minecraft-server/detail",
                        name = "platform-plugin-minecraft-server-detail",
                        title = "服务器详情",
                        icon = "i-ri:file-info-line",
                        component = "minecraft-server/Detail",
                        permission = MinecraftServerPlugin.VIEW_PERMISSION,
                        sort = 20
                ),
                @PluginRoute(
                        path = "/platform/plugins/minecraft-server/admin",
                        name = "platform-plugin-minecraft-server-admin",
                        title = "服务器管理",
                        icon = "i-ri:settings-3-line",
                        component = "minecraft-server/Admin",
                        permission = MinecraftServerPlugin.MANAGE_PERMISSION,
                        sort = 30
                )
        }
)
public class MinecraftServerPlugin implements YuDreamPlugin {

    public static final String CODE = "minecraft-server";
    public static final String VIEW_PERMISSION = "plugin:minecraft-server:view";
    public static final String MANAGE_PERMISSION = "plugin:minecraft-server:manage";
    public static final String REPORT_PERMISSION = "plugin:minecraft-server:report";

    @Override
    public void onEnable(PluginContext context) {
        MinecraftStatusService statusService = new MinecraftStatusService();
        MinecraftServerAppService appService = new MinecraftServerAppService(
                new MinecraftServerDocumentRepository(context.documents()),
                statusService,
                context.framework()
        );
        MinecraftStatusScheduler statusScheduler = new MinecraftStatusScheduler(appService);
        statusScheduler.start();
        context.onDispose(statusScheduler);
        context.registerExtension(PluginMinecraftService.class, appService);
        context.registerHttpController(new MinecraftServerController(new MinecraftServerHttpFacade(appService)));
    }
}
