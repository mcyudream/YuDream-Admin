package online.yudream.base.infra.platform.agent.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.domain.platform.agent.aggregate.AgentWorkflowCheckpoint;
import online.yudream.base.infra.platform.agent.dataobj.AgentWorkflowCheckpointDO;

public final class AgentWorkflowCheckpointInfraMapper {
    private AgentWorkflowCheckpointInfraMapper() { }
    public static AgentWorkflowCheckpointDO toDO(AgentWorkflowCheckpoint value, ObjectMapper json) {
        try {
            var row = new AgentWorkflowCheckpointDO();
            row.setId(value.runId()); row.setUserId(value.userId()); row.setStatus(value.status());
            row.setUpdatedAt(value.updatedAt()); row.setStateJson(json.writeValueAsString(value));
            return row;
        } catch (Exception error) { throw new IllegalStateException("工作流检查点序列化失败", error); }
    }
    public static AgentWorkflowCheckpoint toDomain(AgentWorkflowCheckpointDO row, ObjectMapper json) {
        if (row == null) return null;
        try {
            var state = json.readValue(row.getStateJson(), AgentWorkflowCheckpoint.class);
            return new AgentWorkflowCheckpoint(state.runId(), state.userId(), state.applicationCode(), state.workflowJson(),
                    state.applicationSystemPrompt(), state.command(), state.runKey(), state.frame(), state.modelInvocations(),
                    state.reasoning(), state.toolResults(), state.usage(), row.getStatus(), row.getUpdatedAt(), state.nativePhaseRunIds());
        } catch (Exception error) { throw new IllegalStateException("工作流检查点解析失败", error); }
    }
}
