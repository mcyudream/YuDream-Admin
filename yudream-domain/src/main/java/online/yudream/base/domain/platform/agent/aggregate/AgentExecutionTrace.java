package online.yudream.base.domain.platform.agent.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceSource;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceStatus;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceStep;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次 Agent 工作流执行的完整链路追踪，步骤内嵌，完成后整体落库。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecutionTrace extends BaseDomain {

    private String traceId;
    private AgentTraceSource source;
    private String ownerPluginCode;
    private Long agentId;
    private String agentCode;
    private String agentName;
    private AgentTraceStatus status;
    private String input;
    private String finalOutput;
    private String reasoning;
    private String error;
    private AiUsage usage;
    private List<AgentTraceStep> steps;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;

    public static AgentExecutionTrace start(
            String traceId,
            AgentTraceSource source,
            String ownerPluginCode,
            AgentApplication agent,
            String input
    ) {
        AgentExecutionTrace trace = new AgentExecutionTrace();
        trace.traceId = traceId;
        trace.source = source;
        trace.ownerPluginCode = ownerPluginCode;
        trace.agentId = agent == null ? null : agent.getId();
        trace.agentCode = agent == null ? null : agent.getCode();
        trace.agentName = agent == null ? null : agent.getName();
        trace.status = AgentTraceStatus.RUNNING;
        trace.input = input;
        trace.usage = AiUsage.empty();
        trace.steps = new ArrayList<>();
        trace.startTime = LocalDateTime.now();
        return trace;
    }

    public void succeed(String output, String reasoning, AiUsage usage) {
        this.status = AgentTraceStatus.SUCCEEDED;
        this.finalOutput = output;
        if (reasoning != null && !reasoning.isBlank()) {
            this.reasoning = reasoning;
        }
        this.usage = usage == null ? AiUsage.empty() : usage;
        finish();
    }

    public void fail(String error) {
        this.status = AgentTraceStatus.FAILED;
        this.error = error;
        finish();
    }

    private void finish() {
        this.endTime = LocalDateTime.now();
        this.durationMs = this.startTime == null ? null : Duration.between(this.startTime, this.endTime).toMillis();
    }
}
