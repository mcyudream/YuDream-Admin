package online.yudream.base.domain.platform.ai.service;

import online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot;

/** 在同步框架入口捕获后供私有恢复 profile 保存，不依赖 native Reactor 线程的 ThreadLocal。 */
public final class AiInvocationContextScope implements AutoCloseable {
    private static final ThreadLocal<AiInvocationContextScope> CURRENT = new ThreadLocal<>();
    private final AiInvocationContextScope previous;
    private final AiInvocationContextSnapshot snapshot;
    private boolean closed;
    private AiInvocationContextScope(AiInvocationContextSnapshot snapshot) {
        previous = CURRENT.get(); this.snapshot = snapshot; CURRENT.set(this);
    }
    public static AiInvocationContextScope open(AiInvocationContextSnapshot snapshot) { return new AiInvocationContextScope(snapshot); }
    public static AiInvocationContextSnapshot current() { return CURRENT.get() == null ? null : CURRENT.get().snapshot; }
    @Override public void close() {
        if (closed) return;
        if (CURRENT.get() != this) throw new IllegalStateException("AI 来源作用域必须按后进先出顺序关闭");
        closed = true;
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
