package online.yudream.base.application.platform.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceBucket;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 执行追踪聚合统计。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTraceStatsDTO {

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
