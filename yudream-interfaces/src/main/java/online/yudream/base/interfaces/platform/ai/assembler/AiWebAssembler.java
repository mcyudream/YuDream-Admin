package online.yudream.base.interfaces.platform.ai.assembler;

import online.yudream.base.application.platform.ai.cmd.CmsPageGenerateCmd;
import online.yudream.base.application.platform.ai.dto.AiToolCallDTO;
import online.yudream.base.application.platform.ai.dto.CmsPageGenerateDTO;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.interfaces.platform.ai.request.CmsPageGenerateRequest;
import online.yudream.base.interfaces.platform.ai.res.AiStreamEventRes;
import online.yudream.base.interfaces.platform.ai.res.AiToolCallRes;
import online.yudream.base.interfaces.platform.ai.res.CmsPageGenerateRes;

public class AiWebAssembler {

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

    public static AiStreamEventRes toDeltaRes(String content) {
        return AiStreamEventRes.builder()
                .content(content)
                .build();
    }

    public static AiStreamEventRes toResultRes(CmsPageGenerateDTO dto) {
        return AiStreamEventRes.builder()
                .result(toRes(dto))
                .build();
    }

    public static AiStreamEventRes toToolEventRes(AiAgentToolResult result) {
        return AiStreamEventRes.builder()
                .tool(toToolRes(result))
                .build();
    }

    public static AiStreamEventRes toErrorRes(String message) {
        return AiStreamEventRes.builder()
                .content(message)
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
