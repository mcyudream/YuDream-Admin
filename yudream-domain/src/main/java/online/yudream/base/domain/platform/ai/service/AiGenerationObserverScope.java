package online.yudream.base.domain.platform.ai.service;

import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;

import java.util.UUID;

/** 后台业务运行的观察出口。捕获后可供模型异步线程使用，不依赖其 ThreadLocal。 */
public final class AiGenerationObserverScope implements AutoCloseable {
    public interface Listener {
        void content(String callId, AiGenerationRequest request, String delta);
        void reasoning(String callId, AiGenerationRequest request, String delta);
        void tool(String callId, AiGenerationRequest request, AiAgentToolResult tool);
        void progress(String callId, AiGenerationRequest request, AiGenerationProgress progress);
    }

    public record Observation(String callId, AiGenerationRequest request, Listener listener) {
        public void content(String delta) { listener.content(callId, request, delta); }
        public void reasoning(String delta) { listener.reasoning(callId, request, delta); }
        public void tool(AiAgentToolResult tool) { listener.tool(callId, request, tool); }
        public void progress(AiGenerationProgress progress) { listener.progress(callId, request, progress); }
    }

    private static final ThreadLocal<AiGenerationObserverScope> CURRENT = new ThreadLocal<>();
    private final AiGenerationObserverScope previous;
    private final Listener listener;
    private boolean closed;

    private AiGenerationObserverScope(Listener listener) {
        if (listener == null) throw new IllegalArgumentException("AI 运行观察者不能为空");
        previous = CURRENT.get();
        this.listener = listener;
        CURRENT.set(this);
    }

    public static AiGenerationObserverScope open(Listener listener) { return new AiGenerationObserverScope(listener); }

    public static Observation capture(AiGenerationRequest request) {
        var scope = CURRENT.get();
        return scope == null ? null : new Observation(UUID.randomUUID().toString(), request, scope.listener);
    }

    @Override public void close() {
        if (closed) return;
        if (CURRENT.get() != this) throw new IllegalStateException("AI 观察作用域必须按后进先出顺序关闭");
        closed = true;
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
