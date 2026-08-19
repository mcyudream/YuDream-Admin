package online.yudream.base.interfaces.platform.devtools.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceSource;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceStatus;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 执行追踪列表项响应，长 ID 以字符串输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTraceSummaryRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
