package online.yudream.base.plugin.spi.system.auth;

/**
 * 认证域事件监听器（通知型，fail-open）。
 * 通过 PluginContext.registerExtension(AuthEventListener.class, listener) 注册；
 * 只需要关心的事件重写对应方法即可。
 * 事件在源事务提交后同步派发，实现应只做轻量通知，耗时操作请自行异步化。
 */
public interface AuthEventListener {

    default void onUserRegistered(UserRegisteredEvent event) {
    }

    default void onLoginSucceeded(LoginSucceededEvent event) {
    }
}
