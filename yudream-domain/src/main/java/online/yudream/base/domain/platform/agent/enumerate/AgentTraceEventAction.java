package online.yudream.base.domain.platform.agent.enumerate;

/**
 * Agent 执行追踪事件动作，供开发者工具 SSE 桥按类型推送。
 */
public enum AgentTraceEventAction {
    STARTED,
    STEP,
    COMPLETED,
    FAILED
}
