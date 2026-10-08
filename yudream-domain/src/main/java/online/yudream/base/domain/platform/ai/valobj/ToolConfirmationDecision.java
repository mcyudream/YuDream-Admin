package online.yudream.base.domain.platform.ai.valobj;

/**
 * 用户对某个待确认工具调用的决定（HITL 恢复）。approved=false 时
 * agent 会以“权限被拒”反馈给模型继续推理。
 */
public record ToolConfirmationDecision(String toolCallId, boolean approved) {
}
