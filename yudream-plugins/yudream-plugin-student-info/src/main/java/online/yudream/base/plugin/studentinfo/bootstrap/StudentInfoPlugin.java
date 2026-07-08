package online.yudream.base.plugin.studentinfo.bootstrap;

import online.yudream.base.plugin.spi.annotation.PluginFrontend;
import online.yudream.base.plugin.spi.annotation.PluginPermission;
import online.yudream.base.plugin.spi.annotation.PluginPermissions;
import online.yudream.base.plugin.spi.annotation.PluginRoute;
import online.yudream.base.plugin.spi.annotation.PluginSpec;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.studentinfo.application.service.StudentInfoAppService;
import online.yudream.base.plugin.studentinfo.infrastructure.repository.StudentInfoDocumentRepository;
import online.yudream.base.plugin.studentinfo.interfaces.controller.StudentInfoController;
import online.yudream.base.plugin.studentinfo.interfaces.http.StudentInfoHttpFacade;

@PluginSpec(
        code = StudentInfoPlugin.CODE,
        name = "yudream-student-info",
        version = "1.0.0",
        description = "提供学生学号、班级、学院档案的自助填写与管理员维护能力。"
)
@PluginPermissions({
        @PluginPermission(code = StudentInfoPlugin.VIEW_PERMISSION, name = "查看学生信息", module = "平台插件", description = "查看学生信息插件状态"),
        @PluginPermission(code = StudentInfoPlugin.USER_PERMISSION, name = "填写学生信息", module = "平台插件", description = "填写和查看自己的学生档案"),
        @PluginPermission(code = StudentInfoPlugin.MANAGE_PERMISSION, name = "管理学生信息", module = "平台插件", description = "管理用户学生档案")
})
@PluginFrontend(
        moduleName = "yudreamStudentInfo",
        menuTitle = "学生信息",
        menuIcon = "i-ri:id-card-line",
        menuSort = 35,
        routes = {
                @PluginRoute(
                        path = "/platform/plugins/yudream-student-info",
                        name = "platform-plugin-yudream-student-info-profile",
                        title = "我的学生信息",
                        icon = "i-ri:id-card-line",
                        component = "yudream-student-info/Profile",
                        permission = StudentInfoPlugin.USER_PERMISSION,
                        sort = 20
                ),
                @PluginRoute(
                        path = "/platform/plugins/yudream-student-info/admin/profiles",
                        name = "platform-plugin-yudream-student-info-admin-profiles",
                        title = "学生档案管理",
                        icon = "i-ri:list-check-3-line",
                        component = "yudream-student-info/AdminProfiles",
                        permission = StudentInfoPlugin.MANAGE_PERMISSION,
                        sort = 10
                )
        }
)
public class StudentInfoPlugin implements YuDreamPlugin {

    public static final String CODE = "yudream-student-info";
    public static final String VIEW_PERMISSION = "plugin:yudream-student-info:view";
    public static final String USER_PERMISSION = "plugin:yudream-student-info:user";
    public static final String MANAGE_PERMISSION = "plugin:yudream-student-info:manage";

    @Override
    public void onEnable(PluginContext context) {
        StudentInfoAppService appService = new StudentInfoAppService(new StudentInfoDocumentRepository(context.documents()));
        context.registerHttpController(new StudentInfoController(new StudentInfoHttpFacade(appService, context.framework())));
    }
}
