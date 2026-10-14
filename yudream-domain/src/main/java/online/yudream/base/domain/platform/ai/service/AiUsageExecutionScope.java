package online.yudream.base.domain.platform.ai.service;

/** 同步调用入口捕获可信用户身份；模型异步执行前由网关读出，不从请求配置推断身份。 */
public final class AiUsageExecutionScope implements AutoCloseable {
    private static final ThreadLocal<AiUsageExecutionScope> CURRENT = new ThreadLocal<>();
    private final AiUsageExecutionScope previous;
    private final Long userId;
    private boolean closed;

    private AiUsageExecutionScope(Long userId) {
        this.previous = CURRENT.get();
        this.userId = userId;
        CURRENT.set(this);
    }

    public static AiUsageExecutionScope open(Long userId) {
        return new AiUsageExecutionScope(userId);
    }

    public static Long currentUserId() {
        AiUsageExecutionScope scope = CURRENT.get();
        return scope == null ? null : scope.userId;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        if (CURRENT.get() != this) {
            throw new IllegalStateException("AI 用量作用域必须按后进先出顺序关闭");
        }
        closed = true;
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
