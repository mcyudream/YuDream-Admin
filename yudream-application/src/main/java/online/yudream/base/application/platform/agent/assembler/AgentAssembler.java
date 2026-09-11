package online.yudream.base.application.platform.agent.assembler;

import online.yudream.base.application.platform.agent.dto.AgentApplicationDTO;
import online.yudream.base.application.platform.agent.dto.AgentToolCandidateDTO;
import online.yudream.base.application.platform.agent.dto.AgentToolDTO;
import online.yudream.base.application.platform.agent.dto.AgentTraceDetailDTO;
import online.yudream.base.application.platform.agent.dto.AgentTracePageDTO;
import online.yudream.base.application.platform.agent.dto.AgentTraceStatsDTO;
import online.yudream.base.application.platform.agent.dto.AgentTraceSummaryDTO;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.aggregate.AgentExecutionTrace;
import online.yudream.base.domain.platform.agent.aggregate.AgentTool;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceQuery;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceStats;

import java.util.List;

public final class AgentAssembler {
    private AgentAssembler() {}

    public static AgentTraceSummaryDTO toTraceSummary(AgentExecutionTrace trace) {
        if (trace == null) {
            return null;
        }
        return AgentTraceSummaryDTO.builder()
                .traceId(trace.getTraceId())
                .source(trace.getSource())
                .ownerPluginCode(trace.getOwnerPluginCode())
                .agentId(trace.getAgentId() == null ? null : String.valueOf(trace.getAgentId()))
                .agentCode(trace.getAgentCode())
                .agentName(trace.getAgentName())
                .status(trace.getStatus())
                .input(trace.getInput())
                .error(trace.getError())
                .stepCount(trace.getSteps() == null ? 0 : trace.getSteps().size())
                .durationMs(trace.getDurationMs())
                .startTime(trace.getStartTime())
                .build();
    }

    public static AgentTraceDetailDTO toTraceDetail(AgentExecutionTrace trace) {
        if (trace == null) {
            return null;
        }
        return AgentTraceDetailDTO.builder()
                .traceId(trace.getTraceId())
                .source(trace.getSource())
                .ownerPluginCode(trace.getOwnerPluginCode())
                .agentId(trace.getAgentId() == null ? null : String.valueOf(trace.getAgentId()))
                .agentCode(trace.getAgentCode())
                .agentName(trace.getAgentName())
                .status(trace.getStatus())
                .input(trace.getInput())
                .finalOutput(trace.getFinalOutput())
                .reasoning(trace.getReasoning())
                .error(trace.getError())
                .usage(trace.getUsage())
                .steps(trace.getSteps() == null ? List.of() : trace.getSteps())
                .startTime(trace.getStartTime())
                .endTime(trace.getEndTime())
                .durationMs(trace.getDurationMs())
                .build();
    }

    public static AgentTracePageDTO toTracePage(AgentTraceQuery query, long total, List<AgentExecutionTrace> traces) {
        List<AgentTraceSummaryDTO> list = traces == null
                ? List.of()
                : traces.stream().map(AgentAssembler::toTraceSummary).toList();
        return AgentTracePageDTO.builder()
                .total(total)
                .page(query == null ? 1 : query.page())
                .size(query == null ? list.size() : query.size())
                .list(list)
                .build();
    }

    public static AgentTraceStatsDTO toTraceStats(AgentTraceStats stats) {
        AgentTraceStats value = stats == null ? AgentTraceStats.empty() : stats;
        return AgentTraceStatsDTO.builder()
                .total(value.total())
                .succeeded(value.succeeded())
                .failed(value.failed())
                .running(value.running())
                .avgDurationMs(value.avgDurationMs())
                .maxDurationMs(value.maxDurationMs())
                .promptTokens(value.promptTokens())
                .completionTokens(value.completionTokens())
                .totalTokens(value.totalTokens())
                .sources(value.sources())
                .agents(value.agents())
                .build();
    }

    public static AgentApplicationDTO toDTO(AgentApplication value) {
        if (value == null) return null;
        return AgentApplicationDTO.builder().id(value.getId()).name(value.getName()).code(value.getCode()).description(value.getDescription()).icon(value.getIcon()).systemPrompt(value.getSystemPrompt()).workflowJson(value.getWorkflowJson()).toolCodes(value.getToolCodes()).status(value.getStatus()).sourcePluginCode(value.getSourcePluginCode()).createTime(value.getCreateTime()).updateTime(value.getUpdateTime()).build();
    }
    public static AgentToolCandidateDTO toCandidate(AgentTool value) {
        if (value == null) return null;
        return AgentToolCandidateDTO.builder()
                .code(value.getCode())
                .name(value.getName())
                .description(value.getDescription())
                .permissionCode(value.getPermissionCode())
                .source("python")
                .build();
    }

    public static AgentToolDTO toDTO(AgentTool value) {
        if (value == null) return null;
        return AgentToolDTO.builder().id(value.getId()).name(value.getName()).code(value.getCode()).description(value.getDescription()).type(value.getType()).inputSchemaJson(value.getInputSchemaJson()).outputExampleJson(value.getOutputExampleJson()).pythonCode(value.getPythonCode()).timeoutMillis(value.getTimeoutMillis()).permissionCode(value.getPermissionCode()).enabled(value.getEnabled()).updateTime(value.getUpdateTime()).build();
    }
}
