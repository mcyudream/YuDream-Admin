package online.yudream.base.plugin.authlib.bootstrap;

import online.yudream.base.plugin.authlib.application.service.AuthlibAppService;
import online.yudream.base.plugin.authlib.infrastructure.repository.AuthlibRepository;
import online.yudream.base.plugin.authlib.infrastructure.service.AuthlibCryptoService;
import online.yudream.base.plugin.authlib.interfaces.http.AuthlibHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginDashboardCard;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.annotation.PluginPermission;
import online.yudream.base.plugin.spi.annotation.PluginPermissions;
import online.yudream.base.plugin.spi.annotation.PluginSpec;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

@PluginSpec(
        code = AuthlibInjectorPlugin.CODE,
        name = "Authlib Injector",
        version = "1.0.0",
        description = "基于系统用户和 yudream-skin 角色资料实现 authlib-injector/Yggdrasil 服务端协议。",
        dependencies = {"yudream-skin"}
)
@PluginPermissions({
        @PluginPermission(code = AuthlibInjectorPlugin.VIEW_PERMISSION, name = "查看 Authlib", module = "平台插件", description = "查看 Authlib Injector 插件状态"),
        @PluginPermission(code = AuthlibInjectorPlugin.MANAGE_PERMISSION, name = "管理 Authlib", module = "平台插件", description = "管理 Authlib Injector 配置")
})
@PluginDashboardCard(
        code = "api",
        title = "Authlib API",
        description = "提供 yudream-skin 角色资料的 Yggdrasil 验证服务地址。",
        icon = "i-ri:key-2-line",
        category = "认证服务",
        component = "authlib-injector/EndpointCard",
        dragPayloadTemplate = "authlib-injector:yggdrasil-server:{encodedUrl}",
        tone = "cyan",
        defaultW = 4,
        defaultH = 2,
        minW = 3,
        minH = 2,
        sort = 35
)
public class AuthlibInjectorPlugin implements YuDreamPlugin {

    public static final String CODE = "authlib-injector";
    public static final String VIEW_PERMISSION = "plugin:authlib:view";
    public static final String MANAGE_PERMISSION = "plugin:authlib:manage";

    private AuthlibHttpFacade http;

    @Override
    public void onEnable(PluginContext context) {
        AuthlibRepository repository = new AuthlibRepository(context.documents());
        AuthlibAppService appService = new AuthlibAppService(context, repository, new AuthlibCryptoService(repository));
        this.http = new AuthlibHttpFacade(appService);
    }

    @Override
    public void onDisable(PluginContext context) {
        this.http = null;
    }

    @PluginHttpEndpoint(method = "GET", path = "/", wrapResult = false)
    public PluginHttpResponse metadata(PluginHttpRequest request) {
        return http.metadata(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/status", permission = VIEW_PERMISSION)
    public PluginHttpResponse status(PluginHttpRequest request) {
        return http.status(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/authserver/authenticate", wrapResult = false)
    public PluginHttpResponse authenticate(PluginHttpRequest request) {
        return http.authenticate(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/authserver/refresh", wrapResult = false)
    public PluginHttpResponse refresh(PluginHttpRequest request) {
        return http.refresh(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/authserver/validate", wrapResult = false)
    public PluginHttpResponse validate(PluginHttpRequest request) {
        return http.validate(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/authserver/invalidate", wrapResult = false)
    public PluginHttpResponse invalidate(PluginHttpRequest request) {
        return http.invalidate(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/authserver/signout", wrapResult = false)
    public PluginHttpResponse signout(PluginHttpRequest request) {
        return http.signout(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/sessionserver/session/minecraft/join", wrapResult = false)
    public PluginHttpResponse join(PluginHttpRequest request) {
        return http.join(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/sessionserver/session/minecraft/hasJoined", wrapResult = false)
    public PluginHttpResponse hasJoined(PluginHttpRequest request) {
        return http.hasJoined(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/sessionserver/session/minecraft/profile/{uuid}", wrapResult = false)
    public PluginHttpResponse profile(PluginHttpRequest request) {
        return http.profile(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/api/profiles/minecraft", wrapResult = false)
    public PluginHttpResponse profiles(PluginHttpRequest request) {
        return http.profiles(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/api/user/profile/{uuid}/{textureType}", wrapResult = false)
    public PluginHttpResponse setTexture(PluginHttpRequest request) {
        return http.setTexture(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/api/user/profile/{uuid}/{textureType}", wrapResult = false)
    public PluginHttpResponse clearTexture(PluginHttpRequest request) {
        return http.clearTexture(request);
    }
}
