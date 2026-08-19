package online.yudream.base.domain.platform.agent.valobj;

import online.yudream.base.domain.platform.agent.enumerate.AgentTraceSource;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceStatus;

/**
 * Agent 执行追踪分页查询条件，空字段不参与过滤。
 */
public record AgentTraceQuery(
        AgentTraceSource source,
        String pluginCode,
        AgentTraceStatus status,
        int page,
        int size
) {
    public AgentTraceQuery {
        page = Math.max(page, 1);
        size = size <= 0 ? 20 : Math.min(size, 200);
    }

    public static AgentTraceQuery of(AgentTraceSource source, String pluginCode, AgentTraceStatus status, int page, int size) {
        return new AgentTraceQuery(source, pluginCode, status, page, size);
    }

    public long skip() {
        return (long) (page - 1) * size;
    }
}
