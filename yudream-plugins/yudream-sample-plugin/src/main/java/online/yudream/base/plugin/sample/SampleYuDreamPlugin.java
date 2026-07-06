package online.yudream.base.plugin.sample;

import online.yudream.base.plugin.spi.annotation.PluginFrontend;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.annotation.PluginPermission;
import online.yudream.base.plugin.spi.annotation.PluginRoute;
import online.yudream.base.plugin.spi.annotation.PluginSpec;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;

import java.util.Map;

@PluginSpec(
        code = "sample-plugin",
        name = "样例插件",
        version = "1.0.0",
        description = "YuDream 插件系统样例，演示菜单、权限、HTTP 接口和框架能力调用。"
)
@PluginPermission(
        code = "plugin:sample:view",
        name = "查看样例插件",
        module = "平台插件",
        description = "访问样例插件页面和样例接口"
)
@PluginFrontend(
        moduleName = "samplePlugin",
        sdkVersion = "1.0.0",
        routes = {
                @PluginRoute(
                        path = "/platform/plugins/sample",
                        name = "platform-plugin-sample",
                        title = "样例插件",
                        icon = "i-ri:puzzle-2-line",
                        component = "sample-plugin/Home",
                        permission = "plugin:sample:view",
                        sort = 10
                )
        }
)
public class SampleYuDreamPlugin implements YuDreamPlugin {

    public static final String CODE = "sample-plugin";
    public static final String VIEW_PERMISSION = "plugin:sample:view";

    @PluginHttpEndpoint(method = "GET", path = "/hello", permission = VIEW_PERMISSION)
    public PluginHttpResponse hello(PluginHttpRequest request, PluginContext context) {
        PluginUserProfile profile = context.framework().users()
                .findById(request.principal().userId())
                .orElse(null);
        return PluginHttpResponse.ok(Map.of(
                "message", "Hello from YuDream sample plugin",
                "plugin", CODE,
                "user", profile,
                "roles", context.framework().users().listRoles(request.principal().userId())
        ));
    }
}
