package online.yudream.base.domain.platform.ai.valobj;

import java.util.List;
import java.util.Map;

/**
 * 一次 Harness 会话中待用户确认的工具调用（HITL）。
 * 工具校验返回 ASK 时 agent 暂停并携带本列表；下一轮请求带同 id 的
 * 批准/拒绝决定即可恢复执行。input 为工具调用参数快照（供前端展示）。
 */
public record PendingToolConfirmation(
        String toolCallId,
        String toolName,
        Map<String, Object> input,
        String reason
) {
    public PendingToolConfirmation {
        input = input == null ? Map.of() : Map.copyOf(input);
    }
}
