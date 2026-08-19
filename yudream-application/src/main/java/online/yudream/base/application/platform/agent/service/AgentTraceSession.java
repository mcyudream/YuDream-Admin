package online.yudream.base.application.platform.agent.service;

import online.yudream.base.application.platform.agent.workflow.AgentWorkflowRuntimeResult;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;

/**
 * 一次 Agent 工作流执行的追踪会话，由 AgentExecutionTracer 开启，
 * 运行时服务在各回调点喂入增量；追踪关闭时返回 NOOP，调用方无需判空。
 */
public interface AgentTraceSession {

    AgentTraceSession NOOP = new AgentTraceSession() {
    };

    default boolean active() {
        return false;
    }

    default String traceId() {
        return null;
    }

    default void nodeStarted(String nodeId, String nodeKind, String nodeTitle, String inputSummary) {
    }

    default void nodeCompleted(String nodeId, String outputSummary) {
    }

    default void nodeFailed(String nodeId, String error) {
    }

    default void nodeSkipped(String nodeId, String nodeKind, String nodeTitle) {
    }

    default void reasoningDelta(String delta) {
    }

    default void toolResult(AiAgentToolResult tool) {
    }

    default void succeed(AgentWorkflowRuntimeResult result) {
    }

    default void fail(RuntimeException error) {
    }
}
