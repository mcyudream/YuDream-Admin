package online.yudream.base.domain.platform.ai.valobj;

import java.util.List;

/**
 * Harness Agent 运行结果：正文与推理为全程增量累积，工具结果按执行顺序排列。
 */
public record HarnessRunResult(
        String content,
        String reasoning,
        AiUsage usage,
        List<AiAgentToolResult> toolResults
) {
    public HarnessRunResult {
        content = content == null ? "" : content;
        reasoning = reasoning == null ? "" : reasoning;
        usage = usage == null ? AiUsage.empty() : usage;
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
    }
}
