package online.yudream.base.interfaces.platform.ai.assembler;

import cn.hutool.json.JSONUtil;
import online.yudream.base.application.platform.ai.cmd.CmsPageGenerateCmd;
import online.yudream.base.application.platform.ai.dto.AiToolCallDTO;
import online.yudream.base.application.platform.ai.dto.CmsPageGenerateDTO;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiChatMessage;
import online.yudream.base.interfaces.platform.ai.request.CmsPageGenerateRequest;
import online.yudream.base.interfaces.platform.ai.res.AiStreamEventRes;
import online.yudream.base.interfaces.platform.ai.res.AiToolCallRes;
import online.yudream.base.interfaces.platform.ai.res.AguiStreamEventRes;
import online.yudream.base.interfaces.platform.ai.res.CmsPageGenerateRes;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AiWebAssembler {

    private static final String MODULE_CMS_BUILDER = "cms.builder";

    private AiWebAssembler() {
    }

    public static CmsPageGenerateCmd toCmd(CmsPageGenerateRequest request) {
        CmsPageGenerateCmd cmd = new CmsPageGenerateCmd();
        cmd.setAgentCode(request.getAgentCode());
        cmd.setTitle(request.getTitle());
        cmd.setPrompt(request.getPrompt());
        cmd.setPageType(request.getPageType());
        cmd.setStyle(request.getStyle());
        cmd.setSiteName(request.getSiteName());
        cmd.setProviderCode(request.getProviderCode());
        cmd.setModelCode(StringUtils.hasText(request.getModelCode()) ? request.getModelCode() : request.getModel());
        cmd.setModel(request.getModel());
        cmd.setImageDataUrl(request.getImageDataUrl());
        cmd.setCurrentHtml(request.getCurrentHtml());
        cmd.setCurrentCss(request.getCurrentCss());
        cmd.setCurrentJs(request.getCurrentJs());
        cmd.setCurrentProjectJson(request.getCurrentProjectJson());
        cmd.setCurrentSelectionJson(request.getCurrentSelectionJson());
        cmd.setCmsVariableContextJson(request.getCmsVariableContextJson());
        cmd.setThinkingEnabled(request.isThinkingEnabled());
        cmd.setHistory(toHistory(request.getHistory()));
        return cmd;
    }

    private static List<AiChatMessage> toHistory(List<CmsPageGenerateRequest.ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        return history.stream()
                .filter(item -> item != null && StringUtils.hasText(item.getContent()))
                .map(item -> new AiChatMessage(
                        StringUtils.hasText(item.getRole()) ? item.getRole() : AiChatMessage.ROLE_USER,
                        item.getContent()))
                .toList();
    }

    public static CmsPageGenerateRes toRes(CmsPageGenerateDTO dto) {
        return CmsPageGenerateRes.builder()
                .title(dto.getTitle())
                .summary(dto.getSummary())
                .htmlContent(dto.getHtmlContent())
                .cssContent(dto.getCssContent())
                .jsContent(dto.getJsContent())
                .builderProjectJson(dto.getBuilderProjectJson())
                .markdownContent(dto.getMarkdownContent())
                .tools(dto.getTools() == null ? java.util.List.of() : dto.getTools().stream().map(AiWebAssembler::toToolRes).toList())
                .build();
    }

    public static AiStreamEventRes toDeltaEvent(String traceId, String content) {
        return event("ai.message", "delta", traceId, Map.of("content", content == null ? "" : content));
    }

    public static AiStreamEventRes toProgressEvent(String traceId, String action, String content) {
        return event("ai.progress", action, traceId, Map.of("content", content == null ? "" : content));
    }

    public static AiStreamEventRes toResultEvent(String traceId, CmsPageGenerateDTO dto) {
        return event("ai.result", "complete", traceId, Map.of("result", toRes(dto)));
    }

    public static AiStreamEventRes toToolEvent(String traceId, AiAgentToolResult result) {
        return event("ai.tool", result.action(), traceId, Map.of("tool", toToolRes(result)));
    }

    public static AiStreamEventRes toErrorEvent(String traceId, String message) {
        return event("ai.error", "failed", traceId, Map.of("message", message == null ? "" : message));
    }

    public static AguiStreamEventRes toAguiRunStarted(String traceId) {
        return agui("RUN_STARTED", traceId).threadId("cms-builder").runId(traceId).build();
    }

    public static AguiStreamEventRes toAguiTextChunk(String traceId, String content) {
        return agui("TEXT_MESSAGE_CHUNK", traceId)
                .messageId("assistant-" + traceId)
                .role("assistant")
                .delta(content == null ? "" : content)
                .build();
    }

    public static AguiStreamEventRes toAguiActivitySnapshot(String traceId, String action, String content) {
        return agui("ACTIVITY_SNAPSHOT", traceId)
                .messageId("activity-" + traceId)
                .activityType("cms-progress")
                .content(activityContent(action, content))
                .build();
    }

    public static AguiStreamEventRes toAguiActivityDelta(String traceId, String action, String content) {
        return agui("ACTIVITY_DELTA", traceId)
                .messageId("activity-" + traceId)
                .activityType("cms-progress")
                .patch(List.of(
                        Map.of("op", "replace", "path", "/phase", "value", activityPhase(action)),
                        Map.of("op", "replace", "path", "/title", "value", activityTitle(action)),
                        Map.of("op", "replace", "path", "/content", "value", content == null ? "" : content)
                ))
                .build();
    }

    public static AguiStreamEventRes toAguiCardSnapshot(String traceId, String content) {
        var source = JSONUtil.parseObj(content == null ? "{}" : content.trim());
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("title", source.getStr("title", "AG-UI 卡片"));
        card.put("summary", source.getStr("summary", ""));
        card.put("tone", source.getStr("tone", "info"));
        card.put("fields", source.getJSONArray("fields") == null ? List.of() : source.getJSONArray("fields"));
        card.put("actions", source.getJSONArray("actions") == null ? List.of() : source.getJSONArray("actions"));
        return agui("ACTIVITY_SNAPSHOT", traceId)
                .messageId("card-" + traceId)
                .activityType("agui-card")
                .content(card)
                .build();
    }

    public static AguiStreamEventRes toAguiToolStart(String traceId, String toolCallId, AiAgentToolResult result) {
        return agui("TOOL_CALL_START", traceId)
                .toolCallId(toolCallId)
                .toolCallName(result.toolName())
                .parentMessageId("assistant-" + traceId)
                .build();
    }

    public static AguiStreamEventRes toAguiToolResult(String traceId, String toolCallId, AiAgentToolResult result) {
        return agui("TOOL_CALL_RESULT", traceId)
                .messageId("assistant-" + traceId)
                .role("tool")
                .toolCallId(toolCallId)
                .toolCallName(result.toolName())
                .content(JSONUtil.toJsonStr(toToolRes(result)))
                .build();
    }

    public static AguiStreamEventRes toAguiRunFinished(String traceId, CmsPageGenerateDTO result) {
        return agui("RUN_FINISHED", traceId).threadId("cms-builder").runId(traceId).result(toRes(result)).build();
    }

    public static AguiStreamEventRes toAguiRunError(String traceId, String message) {
        return agui("RUN_ERROR", traceId).message(message == null ? "" : message).build();
    }

    private static AguiStreamEventRes.AguiStreamEventResBuilder agui(String type, String traceId) {
        return AguiStreamEventRes.builder()
                .type(type)
                .timestamp(Instant.now().toEpochMilli())
                .threadId("cms-builder")
                .runId(traceId);
    }

    private static Map<String, Object> activityContent(String action, String content) {
        return Map.of(
                "phase", activityPhase(action),
                "title", activityTitle(action),
                "content", content == null ? "" : content
        );
    }

    private static String activityPhase(String action) {
        return StringUtils.hasText(action) ? action : "progress";
    }

    private static String activityTitle(String action) {
        return switch (action == null ? "" : action) {
            case "analysis" -> "分析画布";
            case "tool-start" -> "调用工具";
            case "tool-complete" -> "工具完成";
            case "subscribed" -> "模型已连接";
            default -> "CMS 构建中";
        };
    }

    private static AiStreamEventRes event(String event, String action, String traceId, Map<String, Object> payload) {
        return AiStreamEventRes.builder()
                .event(event)
                .action(action)
                .module(MODULE_CMS_BUILDER)
                .traceId(traceId)
                .timestamp(Instant.now().toEpochMilli())
                .payload(payload)
                .build();
    }

    private static AiToolCallRes toToolRes(AiToolCallDTO dto) {
        return AiToolCallRes.builder()
                .toolName(dto.getToolName())
                .action(dto.getAction())
                .permissionCode(dto.getPermissionCode())
                .message(dto.getMessage())
                .payload(dto.getPayload())
                .build();
    }

    private static AiToolCallRes toToolRes(AiAgentToolResult result) {
        return AiToolCallRes.builder()
                .toolName(result.toolName())
                .action(result.action())
                .permissionCode(result.permissionCode())
                .message(result.message())
                .payload(result.payload())
                .build();
    }
}
