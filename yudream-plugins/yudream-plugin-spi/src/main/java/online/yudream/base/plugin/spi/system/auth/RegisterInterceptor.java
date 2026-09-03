package online.yudream.base.plugin.spi.system.auth;

/**
 * 注册前置拦截器（veto 型，同步、可否决）。
 * 通过 PluginContext.registerExtension(RegisterInterceptor.class, interceptor) 注册，
 * 多个拦截器按注册优先级从小到大依次执行，任一否决即终止注册。
 * 失败策略为 fail-closed：实现抛出未捕获异常时宿主拒绝本次注册。
 */
public interface RegisterInterceptor {

    ExtensionVeto onBeforeRegister(RegisterAttempt attempt);
}
