package online.yudream.base.domain.platform.agent.valobj;

import online.yudream.base.domain.platform.agent.enumerate.AgentTraceSource;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceStatus;

import java.time.LocalDateTime;

/**
 * Agent 执行追踪分页查询条件，空字段不参与过滤。
 */
public record AgentTraceQuery(
        AgentTraceSource source,
        String pluginCode,
        AgentTraceStatus status,
        String keyword,
        String agentCode,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int page,
        int size
) {
    public AgentTraceQuery {
        page = Math.max(page, 1);
        size = size <= 0 ? 20 : Math.min(size, 200);
        pluginCode = blankToNull(pluginCode);
        keyword = blankToNull(keyword);
        agentCode = blankToNull(agentCode);
    }

    public static AgentTraceQuery of(AgentTraceSource source, String pluginCode, AgentTraceStatus status, int page, int size) {
        return new AgentTraceQuery(source, pluginCode, status, null, null, null, null, page, size);
    }

    public static AgentTraceQuery of(
            AgentTraceSource source,
            String pluginCode,
            AgentTraceStatus status,
            String keyword,
            String agentCode,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int page,
            int size
    ) {
        return new AgentTraceQuery(source, pluginCode, status, keyword, agentCode, startTime, endTime, page, size);
    }

    public long skip() {
        return (long) (page - 1) * size;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
