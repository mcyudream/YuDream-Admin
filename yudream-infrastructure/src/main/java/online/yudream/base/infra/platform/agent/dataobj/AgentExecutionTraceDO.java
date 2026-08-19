package online.yudream.base.infra.platform.agent.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceSource;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceStatus;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceStep;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Document("platformAgentExecutionTrace")
public class AgentExecutionTraceDO extends BaseDO {

    @Indexed(unique = true)
    private String traceId;
    @Indexed
    private AgentTraceSource source;
    @Indexed
    private String ownerPluginCode;
    private Long agentId;
    private String agentCode;
    private String agentName;
    @Indexed
    private AgentTraceStatus status;
    private String input;
    private String finalOutput;
    private String reasoning;
    private String error;
    private AiUsage usage = AiUsage.empty();
    private List<AgentTraceStep> steps = new ArrayList<>();
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
}
