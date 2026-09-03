package online.yudream.base.plugin.spi.system.auth;

/**
 * 登录前置拦截器（veto 型，同步、可否决），在凭据校验之前执行。
 * 通过 PluginContext.registerExtension(LoginInterceptor.class, interceptor) 注册。
 * 失败策略为 fail-closed：实现抛出未捕获异常时宿主拒绝本次登录。
 */
public interface LoginInterceptor {

    ExtensionVeto onBeforeLogin(LoginAttempt attempt);
}
