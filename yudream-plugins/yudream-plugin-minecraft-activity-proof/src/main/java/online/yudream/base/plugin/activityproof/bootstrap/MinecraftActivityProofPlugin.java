package online.yudream.base.plugin.activityproof.bootstrap;

import online.yudream.base.plugin.activityproof.application.service.ActivityProofAppService;
import online.yudream.base.plugin.activityproof.infrastructure.repository.ActivityProofDocumentRepository;
import online.yudream.base.plugin.activityproof.interfaces.controller.ActivityProofController;
import online.yudream.base.plugin.activityproof.interfaces.http.ActivityProofHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginFrontend;
import online.yudream.base.plugin.spi.annotation.PluginPermission;
import online.yudream.base.plugin.spi.annotation.PluginPermissions;
import online.yudream.base.plugin.spi.annotation.PluginRoute;
import online.yudream.base.plugin.spi.annotation.PluginSpec;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;

@PluginSpec(
        code = MinecraftActivityProofPlugin.CODE,
        name = "minecraft-activity-proof",
        version = "1.0.0",
        description = "联动 Minecraft 在线记录与学生信息，按 Word 模板导出活动证明。",
        dependencies = { "minecraft-server", "yudream-student-info" }
)
@PluginPermissions({
        @PluginPermission(code = MinecraftActivityProofPlugin.VIEW_PERMISSION, name = "查看 MC 活动证明", module = "平台插件", description = "查看活动证明导出状态和记录"),
        @PluginPermission(code = MinecraftActivityProofPlugin.MANAGE_PERMISSION, name = "管理 MC 活动证明", module = "平台插件", description = "维护模板、映射并导出活动证明")
})
@PluginFrontend(
        moduleName = "minecraftActivityProof",
        menuTitle = "学生信息",
        menuIcon = "i-ri:id-card-line",
        menuSort = 35,
        routes = {
                @PluginRoute(
                        path = "/platform/plugins/yudream-student-info/activity-proof",
                        name = "platform-plugin-yudream-student-info-activity-proof",
                        title = "活动证明导出",
                        icon = "i-ri:file-word-2-line",
                        component = "minecraft-activity-proof/Export",
                        permission = MinecraftActivityProofPlugin.ACCESS_MANAGE_PERMISSION,
                        sort = 10
                ),
                @PluginRoute(
                        path = "/platform/plugins/yudream-student-info/activity-proof/records",
                        name = "platform-plugin-yudream-student-info-activity-proof-records",
                        title = "活动证明记录",
                        icon = "i-ri:file-list-3-line",
                        component = "minecraft-activity-proof/Records",
                        permission = MinecraftActivityProofPlugin.ACCESS_MANAGE_PERMISSION,
                        sort = 11
                ),
                @PluginRoute(
                        path = "/platform/plugins/yudream-student-info/my-activity-proofs",
                        name = "platform-plugin-yudream-student-info-my-activity-proofs",
                        title = "我的活动证明",
                        icon = "i-ri:verified-badge-line",
                        component = "minecraft-activity-proof/Mine",
                        permission = MinecraftActivityProofPlugin.ACCESS_USER_PERMISSION,
                        sort = 12
                )
        }
)
public class MinecraftActivityProofPlugin implements YuDreamPlugin {

    public static final String CODE = "minecraft-activity-proof";
    public static final String VIEW_PERMISSION = "plugin:minecraft-activity-proof:view";
    public static final String MANAGE_PERMISSION = "plugin:minecraft-activity-proof:manage";
    public static final String ACCESS_VIEW_PERMISSION = "plugin:yudream-student-info:view";
    public static final String ACCESS_USER_PERMISSION = "plugin:yudream-student-info:user";
    public static final String ACCESS_MANAGE_PERMISSION = "plugin:yudream-student-info:manage";

    @Override
    public void onEnable(PluginContext context) {
        ActivityProofAppService appService = new ActivityProofAppService(
                new ActivityProofDocumentRepository(context.documents()),
                context.files(),
                context.framework()
        );
        context.registerHttpController(new ActivityProofController(new ActivityProofHttpFacade(appService)));
    }
}
