package online.yudream.base.interfaces.platform.agent.assembler;

import online.yudream.base.application.platform.agent.cmd.AgentApplicationSaveCmd;
import online.yudream.base.application.platform.agent.cmd.AgentAttachmentCmd;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.cmd.AgentToolSaveCmd;
import online.yudream.base.application.platform.agent.dto.AgentApplicationDTO;
import online.yudream.base.application.platform.agent.dto.AgentCatalogDTO;
import online.yudream.base.application.platform.agent.dto.AgentDebugEventDTO;
import online.yudream.base.application.platform.agent.dto.AgentModelDTO;
import online.yudream.base.application.platform.agent.dto.AgentRunDTO;
import online.yudream.base.application.platform.agent.dto.AgentToolCandidateDTO;
import online.yudream.base.application.platform.agent.dto.AgentToolDTO;
import online.yudream.base.application.platform.agent.dto.AgentTraceDetailDTO;
import online.yudream.base.application.platform.agent.dto.AgentTracePageDTO;
import online.yudream.base.application.platform.agent.dto.AgentTraceStatsDTO;
import online.yudream.base.application.platform.agent.dto.AgentTraceSummaryDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolType;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceSource;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceStatus;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceQuery;
import online.yudream.base.interfaces.platform.agent.request.AgentApplicationSaveRequest;
import online.yudream.base.interfaces.platform.agent.request.AgentRunRequest;
import online.yudream.base.interfaces.platform.agent.request.AgentToolSaveRequest;
import online.yudream.base.interfaces.platform.agent.res.AgentApplicationRes;
import online.yudream.base.interfaces.platform.agent.res.AgentCatalogRes;
import online.yudream.base.interfaces.platform.agent.res.AgentDebugEventRes;
import online.yudream.base.interfaces.platform.agent.res.AgentModelRes;
import online.yudream.base.interfaces.platform.agent.res.AgentKnowledgeSpaceRes;
import online.yudream.base.interfaces.platform.agent.res.AgentRunRes;
import online.yudream.base.interfaces.platform.agent.res.AgentToolCandidateRes;
import online.yudream.base.interfaces.platform.agent.res.AgentToolRes;
import online.yudream.base.interfaces.platform.agent.res.AgentTraceDetailRes;
import online.yudream.base.interfaces.platform.agent.res.AgentTracePageRes;
import online.yudream.base.interfaces.platform.agent.res.AgentTraceStatsRes;
import online.yudream.base.interfaces.platform.agent.res.AgentTraceSummaryRes;
import online.yudream.base.interfaces.platform.ai.res.AiToolCallRes;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport.SecurityPrincipal;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

public final class AgentWebAssembler {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private AgentWebAssembler() {}
    public static AgentApplicationSaveCmd toCmd(AgentApplicationSaveRequest request) { return toCmd(null, request); }
    public static AgentApplicationSaveCmd toCmd(Long id, AgentApplicationSaveRequest request) { AgentApplicationSaveCmd cmd = new AgentApplicationSaveCmd(); cmd.setId(id); cmd.setName(request.getName()); cmd.setCode(request.getCode()); cmd.setDescription(request.getDescription()); cmd.setIcon(request.getIcon()); cmd.setSystemPrompt(request.getSystemPrompt()); cmd.setWorkflowJson(request.getWorkflowJson()); cmd.setToolCodes(request.getToolCodes()); cmd.setStatus(request.getStatus()); return cmd; }
    public static AgentToolSaveCmd toCmd(AgentToolSaveRequest request) { return toCmd(null, request); }
    public static AgentToolSaveCmd toCmd(Long id, AgentToolSaveRequest request) { AgentToolSaveCmd cmd = new AgentToolSaveCmd(); cmd.setId(id); cmd.setName(request.getName()); cmd.setCode(request.getCode()); cmd.setDescription(request.getDescription()); cmd.setType(AgentToolType.PYTHON); cmd.setInputSchemaJson(request.getInputSchemaJson()); cmd.setOutputExampleJson(request.getOutputExampleJson()); cmd.setPythonCode(request.getPythonCode()); cmd.setTimeoutMillis(request.getTimeoutMillis()); cmd.setPermissionCode(request.getPermissionCode()); cmd.setEnabled(request.getEnabled()); return cmd; }
    public static AgentRunCmd toRunCmd(Long id, AgentRunRequest request) { return toRunCmd(id, request, null); }
    public static AgentRunCmd toRunCmd(Long id, AgentRunRequest request, SecurityPrincipal principal) { AgentRunCmd cmd = new AgentRunCmd(); cmd.setApplicationId(id); cmd.setInput(request.getInput()); cmd.setProviderCode(request.getProviderCode()); cmd.setModelCode(request.getModelCode()); cmd.setImageDataUrl(request.getImageDataUrl()); cmd.setImageDataUrls(request.getImageDataUrls() == null ? java.util.List.of() : java.util.List.copyOf(request.getImageDataUrls())); cmd.setAttachments(request.getAttachments() == null ? java.util.List.of() : request.getAttachments().stream().map(item -> new AgentAttachmentCmd(item.getName(), item.getContentType(), item.getSize(), item.getDataUrl())).toList()); if (principal != null) { cmd.setPermissionCodes(principal.permissions() == null ? java.util.List.of() : java.util.List.copyOf(principal.permissions())); cmd.setPermissionContextExplicit(true); } return cmd; }
    public static PageResult<AgentApplicationRes> toApplicationPage(PageResult<AgentApplicationDTO> page) { return new PageResult<>(page.getRecords().stream().map(AgentWebAssembler::toRes).toList(), page.getTotal(), page.getPage(), page.getSize()); }
    public static PageResult<AgentToolRes> toToolPage(PageResult<AgentToolDTO> page) { return new PageResult<>(page.getRecords().stream().map(AgentWebAssembler::toRes).toList(), page.getTotal(), page.getPage(), page.getSize()); }
    public static PageResult<AgentToolCandidateRes> toToolCandidatePage(PageResult<AgentToolCandidateDTO> page) { return new PageResult<>(page.getRecords().stream().map(AgentWebAssembler::toCandidateRes).toList(), page.getTotal(), page.getPage(), page.getSize()); }
    public static AgentToolCandidateRes toCandidateRes(AgentToolCandidateDTO value) { return AgentToolCandidateRes.builder().code(value.getCode()).name(value.getName()).description(value.getDescription()).permissionCode(value.getPermissionCode()).source(value.getSource()).build(); }
    public static AgentApplicationRes toRes(AgentApplicationDTO value) { return AgentApplicationRes.builder().id(String.valueOf(value.getId())).name(value.getName()).code(value.getCode()).description(value.getDescription()).icon(value.getIcon()).systemPrompt(value.getSystemPrompt()).workflowJson(value.getWorkflowJson()).toolCodes(value.getToolCodes()).status(value.getStatus()).sourcePluginCode(value.getSourcePluginCode()).createTime(value.getCreateTime()).updateTime(value.getUpdateTime()).build(); }
    public static AgentToolRes toRes(AgentToolDTO value) { return AgentToolRes.builder().id(String.valueOf(value.getId())).name(value.getName()).code(value.getCode()).description(value.getDescription()).type(value.getType()).inputSchemaJson(value.getInputSchemaJson()).outputExampleJson(value.getOutputExampleJson()).pythonCode(value.getPythonCode()).timeoutMillis(value.getTimeoutMillis()).permissionCode(value.getPermissionCode()).enabled(value.getEnabled()).updateTime(value.getUpdateTime()).build(); }
    public static AgentRunRes toRes(AgentRunDTO value) { return AgentRunRes.builder().content(value.getContent()).reasoning(value.getReasoning()).toolResults(value.getToolResults().stream().map(tool -> AiToolCallRes.builder().toolName(tool.toolName()).action(tool.action()).permissionCode(tool.permissionCode()).message(tool.message()).payload(tool.payload()).build()).toList()).build(); }
    public static AgentModelRes toRes(AgentModelDTO value) { return AgentModelRes.builder().providerCode(value.providerCode()).providerName(value.providerName()).modelCode(value.modelCode()).modelName(value.modelName()).kind(value.kind()).vision(value.vision()).configured(value.configured()).defaultModel(value.defaultModel()).build(); }
    public static AgentCatalogRes toRes(AgentCatalogDTO value) { return AgentCatalogRes.builder().knowledgeSpaces(value.knowledgeSpaces().stream().map(space -> AgentKnowledgeSpaceRes.builder().slug(space.slug()).name(space.name()).embeddingProviderCode(space.embeddingProviderCode()).embeddingModelCode(space.embeddingModelCode()).topK(space.topK()).graphEnabled(space.graphEnabled()).rerankEnabled(space.rerankEnabled()).build()).toList()).models(value.models().stream().map(AgentWebAssembler::toRes).toList()).build(); }
    public static AgentDebugEventRes toDebugRunStarted(String runId) { return debugEvent("RUN_STARTED", runId).status("RUNNING").message("Agent 调试已开始").build(); }
    public static AgentDebugEventRes toDebugNodeEvent(String runId, AgentDebugEventDTO value) { return debugEvent("NODE_" + value.status(), runId).nodeId(value.nodeId()).nodeKind(value.nodeKind()).nodeTitle(value.nodeTitle()).status(value.status()).message(value.message()).build(); }
    public static AgentDebugEventRes toDebugTextChunk(String runId, String delta) { return debugEvent("TEXT_MESSAGE_CHUNK", runId).delta(delta == null ? "" : delta).build(); }
    public static AgentDebugEventRes toDebugThinkingStart(String runId) { return debugEvent("THINKING_START", runId).messageId("thinking-" + runId).build(); }
    public static AgentDebugEventRes toDebugThinkingContent(String runId, String delta) { return debugEvent("THINKING_CONTENT", runId).messageId("thinking-" + runId).delta(delta == null ? "" : delta).build(); }
    public static AgentDebugEventRes toDebugThinkingEnd(String runId) { return debugEvent("THINKING_END", runId).messageId("thinking-" + runId).build(); }
    public static AgentDebugEventRes toDebugToolResult(String runId, online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult value) { return debugEvent("TOOL_CALL_RESULT", runId).tool(AiToolCallRes.builder().toolName(value.toolName()).action(value.action()).permissionCode(value.permissionCode()).message(value.message()).payload(value.payload()).build()).build(); }
    public static AgentDebugEventRes toDebugRunFinished(String runId, AgentRunDTO value) { return debugEvent("RUN_FINISHED", runId).status("COMPLETED").message("Agent 调试完成").result(toRes(value)).build(); }
    public static AgentDebugEventRes toDebugRunError(String runId, String message) { return debugEvent("RUN_ERROR", runId).status("FAILED").message(message == null ? "Agent 调试失败" : message).build(); }
    private static AgentDebugEventRes.AgentDebugEventResBuilder debugEvent(String type, String runId) { return AgentDebugEventRes.builder().type(type).threadId("agent-debug").runId(runId).timestamp(Instant.now().toEpochMilli()); }

    public static AgentTraceQuery toTraceQuery(
            String source,
            String pluginCode,
            String status,
            String keyword,
            String agentCode,
            String startTime,
            String endTime,
            Integer page,
            Integer size
    ) {
        return AgentTraceQuery.of(
                parseEnum(AgentTraceSource.class, source, "来源"),
                pluginCode,
                parseEnum(AgentTraceStatus.class, status, "状态"),
                keyword,
                agentCode,
                parseDateTime(startTime, "开始时间"),
                parseDateTime(endTime, "结束时间"),
                page == null ? 1 : page,
                size == null ? 20 : size
        );
    }

    public static AgentTracePageRes toTracePage(AgentTracePageDTO dto) {
        List<AgentTraceSummaryRes> list = dto.getList() == null
                ? List.of()
                : dto.getList().stream().map(AgentWebAssembler::toTraceSummary).toList();
        return AgentTracePageRes.builder()
                .total(dto.getTotal())
                .page(dto.getPage())
                .size(dto.getSize())
                .list(list)
                .build();
    }

    public static AgentTraceSummaryRes toTraceSummary(AgentTraceSummaryDTO dto) {
        return AgentTraceSummaryRes.builder()
                .traceId(dto.getTraceId())
                .source(dto.getSource())
                .ownerPluginCode(dto.getOwnerPluginCode())
                .agentId(dto.getAgentId())
                .agentCode(dto.getAgentCode())
                .agentName(dto.getAgentName())
                .status(dto.getStatus())
                .input(dto.getInput())
                .error(dto.getError())
                .stepCount(dto.getStepCount())
                .durationMs(dto.getDurationMs())
                .startTime(dto.getStartTime())
                .build();
    }

    public static AgentTraceDetailRes toTraceDetail(AgentTraceDetailDTO dto) {
        return AgentTraceDetailRes.builder()
                .traceId(dto.getTraceId())
                .source(dto.getSource())
                .ownerPluginCode(dto.getOwnerPluginCode())
                .agentId(dto.getAgentId())
                .agentCode(dto.getAgentCode())
                .agentName(dto.getAgentName())
                .status(dto.getStatus())
                .input(dto.getInput())
                .finalOutput(dto.getFinalOutput())
                .reasoning(dto.getReasoning())
                .error(dto.getError())
                .usage(dto.getUsage())
                .steps(dto.getSteps())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .durationMs(dto.getDurationMs())
                .build();
    }

    public static AgentTraceStatsRes toTraceStats(AgentTraceStatsDTO dto) {
        return AgentTraceStatsRes.builder()
                .total(dto.getTotal())
                .succeeded(dto.getSucceeded())
                .failed(dto.getFailed())
                .running(dto.getRunning())
                .avgDurationMs(dto.getAvgDurationMs())
                .maxDurationMs(dto.getMaxDurationMs())
                .promptTokens(dto.getPromptTokens())
                .completionTokens(dto.getCompletionTokens())
                .totalTokens(dto.getTotalTokens())
                .sources(dto.getSources())
                .agents(dto.getAgents())
                .build();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String label) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException("非法的" + label + "过滤值：" + value);
        }
    }

    private static LocalDateTime parseDateTime(String value, String label) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim().replace('T', ' ');
        if (text.length() == 10) {
            text = text + " 00:00:00";
        } else if (text.length() == 16) {
            text = text + ":00";
        }
        try {
            if (text.length() > 19) {
                return LocalDateTime.parse(text.substring(0, 19), DATE_TIME);
            }
            return LocalDateTime.parse(text, DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new BizException("非法的" + label + "：" + value);
        }
    }
}
