package online.yudream.base.domain.platform.agent.event;

import online.yudream.base.domain.platform.agent.aggregate.AgentExecutionTrace;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceEventAction;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceSource;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceStatus;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceStep;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;

import java.time.Instant;

/**
 * Agent 执行追踪增量事件，由应用层经 Spring ApplicationEventPublisher 发布，
 * 开发者工具 SSE 桥订阅后推送到调试抽屉；STEP 动作携带刚追加/更新的步骤。
 */
public record AgentTraceEvent(
        String traceId,
        AgentTraceEventAction action,
        AgentTraceSource source,
        String ownerPluginCode,
        String agentCode,
        String agentName,
        AgentTraceStatus status,
        AgentTraceStep step,
        String error,
        Long durationMs,
        AiUsage usage,
        Instant occurredAt
) {
    public static AgentTraceEvent started(AgentExecutionTrace trace) {
        return of(trace, AgentTraceEventAction.STARTED, null);
    }

    public static AgentTraceEvent appended(AgentExecutionTrace trace, AgentTraceStep step) {
        return of(trace, AgentTraceEventAction.STEP, step);
    }

    public static AgentTraceEvent completed(AgentExecutionTrace trace) {
        return of(trace, AgentTraceEventAction.COMPLETED, null);
    }

    public static AgentTraceEvent failed(AgentExecutionTrace trace) {
        return of(trace, AgentTraceEventAction.FAILED, null);
    }

    private static AgentTraceEvent of(AgentExecutionTrace trace, AgentTraceEventAction action, AgentTraceStep step) {
        return new AgentTraceEvent(
                trace.getTraceId(), action, trace.getSource(), trace.getOwnerPluginCode(),
                trace.getAgentCode(), trace.getAgentName(), trace.getStatus(), step,
                trace.getError(), trace.getDurationMs(), trace.getUsage(), Instant.now()
        );
    }
}
