package online.yudream.base.domain.platform.agent.enumerate;

/**
 * Agent 执行追踪中单步（工作流节点或工具调用）的状态。
 */
public enum AgentTraceStepStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED
}
