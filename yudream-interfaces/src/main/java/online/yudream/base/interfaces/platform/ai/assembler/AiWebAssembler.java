package online.yudream.base.interfaces.platform.ai.assembler;

import online.yudream.base.application.platform.ai.cmd.CmsPageGenerateCmd;
import online.yudream.base.application.platform.ai.dto.AiToolCallDTO;
import online.yudream.base.application.platform.ai.dto.CmsPageGenerateDTO;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.interfaces.platform.ai.request.CmsPageGenerateRequest;
import online.yudream.base.interfaces.platform.ai.res.AiStreamEventRes;
import online.yudream.base.interfaces.platform.ai.res.AiToolCallRes;
import online.yudream.base.interfaces.platform.ai.res.CmsPageGenerateRes;

import java.time.Instant;
import java.util.Map;

public class AiWebAssembler {

    private static final String MODULE_CMS_BUILDER = "cms.builder";

    private AiWebAssembler() {
    }

    public static CmsPageGenerateCmd toCmd(CmsPageGenerateRequest request) {
        CmsPageGenerateCmd cmd = new CmsPageGenerateCmd();
        cmd.setTitle(request.getTitle());
        cmd.setPrompt(request.getPrompt());
        cmd.setPageType(request.getPageType());
        cmd.setStyle(request.getStyle());
        cmd.setSiteName(request.getSiteName());
        cmd.setModel(request.getModel());
        cmd.setImageDataUrl(request.getImageDataUrl());
        cmd.setCurrentHtml(request.getCurrentHtml());
        cmd.setCurrentCss(request.getCurrentCss());
        cmd.setCurrentProjectJson(request.getCurrentProjectJson());
        cmd.setThinkingEnabled(true);
        return cmd;
    }

    public static CmsPageGenerateRes toRes(CmsPageGenerateDTO dto) {
        return CmsPageGenerateRes.builder()
                .title(dto.getTitle())
                .summary(dto.getSummary())
                .htmlContent(dto.getHtmlContent())
                .cssContent(dto.getCssContent())
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
