package online.yudream.base.domain.platform.agent.valobj;

import online.yudream.base.domain.platform.agent.enumerate.AgentTraceStepStatus;

import java.time.LocalDateTime;

/**
 * Agent 执行追踪中的单步快照：一个工作流节点或一次工具调用。
 * 思考过程归因于步骤执行期间仍处于打开状态的最近节点；
 * 工具调用作为独立步骤，nodeId 为空、nodeKind 固定为 tool。
 */
public record AgentTraceStep(
        int seq,
        String nodeId,
        String nodeKind,
        String nodeTitle,
        AgentTraceStepStatus status,
        String inputSummary,
        String outputSummary,
        String reasoning,
        String toolName,
        String toolDetail,
        String message,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long durationMs
) {
}
