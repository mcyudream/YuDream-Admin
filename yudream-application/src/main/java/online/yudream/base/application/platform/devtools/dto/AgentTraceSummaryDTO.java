package online.yudream.base.application.platform.devtools.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceSource;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceStatus;

import java.time.LocalDateTime;

/**
 * Agent 执行追踪列表项，长 ID 以字符串输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTraceSummaryDTO {

    private String traceId;
    private AgentTraceSource source;
    private String ownerPluginCode;
    private String agentId;
    private String agentCode;
    private String agentName;
    private AgentTraceStatus status;
    private String input;
    private String error;
    private int stepCount;
    private Long durationMs;
    private LocalDateTime startTime;
}
