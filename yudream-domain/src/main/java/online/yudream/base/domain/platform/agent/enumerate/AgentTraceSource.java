package online.yudream.base.domain.platform.agent.enumerate;

/**
 * Agent 执行链路追踪来源，标记一次工作流执行由哪个入口触发。
 */
public enum AgentTraceSource {
    CHAT,
    WIKI,
    CMS,
    DEBUG,
    PLUGIN,
    SYSTEM
}
