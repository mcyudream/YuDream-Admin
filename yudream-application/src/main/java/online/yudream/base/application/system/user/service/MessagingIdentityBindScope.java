package online.yudream.base.application.system.user.service;

import online.yudream.base.domain.platform.milky.enumerate.MilkyConnectionProtocol;

/**
 * 当前消息分发线程上的绑定上下文。插件仍可调用 {@code bindQqOnce(userId, event.userId())}，
 * 宿主据此判断是 Milky QQ 还是官方 openid，避免把 openid 写入 {@code User.qq}。
 */
public final class MessagingIdentityBindScope implements AutoCloseable {

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private final Context previous;
    private final Context installed;
    private boolean closed;

    private MessagingIdentityBindScope(Context context) {
        previous = CURRENT.get();
        installed = context;
        CURRENT.set(context);
    }

    public static MessagingIdentityBindScope open(Context context) {
        return new MessagingIdentityBindScope(context);
    }

    public static Context current() {
        return CURRENT.get();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        if (CURRENT.get() != installed) {
            throw new IllegalStateException("消息身份绑定作用域必须按后进先出顺序关闭");
        }
        closed = true;
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    public record Context(MilkyConnectionProtocol protocol, Long connectionId, String appId, String scene,
                          String groupOpenid, String identity) {
    }
}
