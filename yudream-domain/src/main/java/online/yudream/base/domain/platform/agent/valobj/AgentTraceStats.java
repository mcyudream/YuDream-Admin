package online.yudream.base.domain.platform.agent.valobj;

import java.util.List;

/**
 * Agent 执行追踪聚合统计，用于日志页概览。
 */
public record AgentTraceStats(
        long total,
        long succeeded,
        long failed,
        long running,
        long avgDurationMs,
        long maxDurationMs,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        List<AgentTraceBucket> sources,
        List<AgentTraceBucket> agents
) {
    public AgentTraceStats {
        sources = sources == null ? List.of() : List.copyOf(sources);
        agents = agents == null ? List.of() : List.copyOf(agents);
    }

    public static AgentTraceStats empty() {
        return new AgentTraceStats(0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of());
    }
}
