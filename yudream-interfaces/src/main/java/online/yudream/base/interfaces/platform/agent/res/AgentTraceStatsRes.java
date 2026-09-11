package online.yudream.base.interfaces.platform.agent.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceBucket;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 执行追踪聚合统计响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTraceStatsRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private long total;
    private long succeeded;
    private long failed;
    private long running;
    private long avgDurationMs;
    private long maxDurationMs;
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;

    @Builder.Default
    private List<AgentTraceBucket> sources = new ArrayList<>();

    @Builder.Default
    private List<AgentTraceBucket> agents = new ArrayList<>();
}
