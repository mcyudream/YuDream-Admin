package online.yudream.base.domain.platform.agent.valobj;

/**
 * Agent 执行追踪按维度聚合的计数桶。
 */
public record AgentTraceBucket(
        String key,
        String label,
        long count
) {
}
