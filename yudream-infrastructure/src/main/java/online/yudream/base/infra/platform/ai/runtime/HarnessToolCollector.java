package online.yudream.base.infra.platform.ai.runtime;

import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * RuntimeContext 中的按次工具结果收集器：共享的 AgentToolAdapter 在执行后把结果
 * 记入本次调用的收集器（并发安全），运行时据此回填消息与回调。
 */
public final class HarnessToolCollector {

    private final List<AiAgentToolResult> results = new ArrayList<>();
    private final Consumer<AiAgentToolResult> onTool;

    public HarnessToolCollector(Consumer<AiAgentToolResult> onTool) {
        this.onTool = onTool;
    }

    public synchronized void record(AiAgentToolResult result) {
        if (result == null) {
            return;
        }
        results.add(result);
        if (onTool != null) {
            onTool.accept(result);
        }
    }

    public synchronized List<AiAgentToolResult> snapshot() {
        return List.copyOf(results);
    }
}
