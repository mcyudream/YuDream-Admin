package online.yudream.base.infra.platform.agent.mapper;

import online.yudream.base.domain.platform.agent.aggregate.AgentExecutionTrace;
import online.yudream.base.infra.platform.agent.dataobj.AgentExecutionTraceDO;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 执行追踪 domain ↔ dataobj 转换，只服务仓储实现。
 */
public final class AgentTraceInfraMapper {

    private AgentTraceInfraMapper() {
    }

    public static AgentExecutionTrace toDomain(AgentExecutionTraceDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return AgentExecutionTrace.builder()
                .id(dataObj.getId())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .traceId(dataObj.getTraceId())
                .source(dataObj.getSource())
                .ownerPluginCode(dataObj.getOwnerPluginCode())
                .agentId(dataObj.getAgentId())
                .agentCode(dataObj.getAgentCode())
                .agentName(dataObj.getAgentName())
                .status(dataObj.getStatus())
                .input(dataObj.getInput())
                .finalOutput(dataObj.getFinalOutput())
                .reasoning(dataObj.getReasoning())
                .error(dataObj.getError())
                .usage(dataObj.getUsage())
                .steps(dataObj.getSteps() == null ? new ArrayList<>() : new ArrayList<>(dataObj.getSteps()))
                .startTime(dataObj.getStartTime())
                .endTime(dataObj.getEndTime())
                .durationMs(dataObj.getDurationMs())
                .build();
    }

    public static AgentExecutionTraceDO toDO(AgentExecutionTrace domain) {
        if (domain == null) {
            return null;
        }
        AgentExecutionTraceDO dataObj = new AgentExecutionTraceDO();
        dataObj.setId(domain.getId());
        dataObj.setVersion(domain.getVersion());
        dataObj.setCreateTime(domain.getCreateTime());
        dataObj.setUpdateTime(domain.getUpdateTime());
        dataObj.setTraceId(domain.getTraceId());
        dataObj.setSource(domain.getSource());
        dataObj.setOwnerPluginCode(domain.getOwnerPluginCode());
        dataObj.setAgentId(domain.getAgentId());
        dataObj.setAgentCode(domain.getAgentCode());
        dataObj.setAgentName(domain.getAgentName());
        dataObj.setStatus(domain.getStatus());
        dataObj.setInput(domain.getInput());
        dataObj.setFinalOutput(domain.getFinalOutput());
        dataObj.setReasoning(domain.getReasoning());
        dataObj.setError(domain.getError());
        dataObj.setUsage(domain.getUsage());
        dataObj.setSteps(domain.getSteps() == null ? new ArrayList<>() : List.copyOf(domain.getSteps()));
        dataObj.setStartTime(domain.getStartTime());
        dataObj.setEndTime(domain.getEndTime());
        dataObj.setDurationMs(domain.getDurationMs());
        return dataObj;
    }
}
