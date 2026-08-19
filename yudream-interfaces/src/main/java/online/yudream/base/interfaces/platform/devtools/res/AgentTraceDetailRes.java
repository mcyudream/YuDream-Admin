package online.yudream.base.interfaces.platform.devtools.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceSource;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceStatus;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceStep;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 执行追踪详情响应，长 ID 以字符串输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTraceDetailRes implements Serializable {

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
    private String finalOutput;
    private String reasoning;
    private String error;
    private AiUsage usage;

    @Builder.Default
    private List<AgentTraceStep> steps = new ArrayList<>();

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
}
