package online.yudream.base.application.platform.ai.assembler;

import online.yudream.base.application.platform.ai.dto.CmsPageGenerateDTO;
import online.yudream.base.application.platform.ai.dto.AiToolCallDTO;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;

import java.util.List;

public class AiAssembler {

    private AiAssembler() {
    }

    public static CmsPageGenerateDTO toDTO(AiGenerationResult result) {
        return CmsPageGenerateDTO.builder()
                .title(result.title())
                .summary(result.summary())
                .htmlContent(result.htmlContent())
                .cssContent(result.cssContent())
                .builderProjectJson(result.builderProjectJson())
                .markdownContent(result.markdownContent())
                .build();
    }

    public static AiToolCallDTO toToolDTO(AiAgentToolResult result) {
        return AiToolCallDTO.builder()
                .toolName(result.toolName())
                .action(result.action())
                .permissionCode(result.permissionCode())
                .message(result.message())
                .payload(result.payload())
                .build();
    }

    public static CmsPageGenerateDTO withTools(CmsPageGenerateDTO dto, List<AiAgentToolResult> results) {
        dto.setTools(results == null ? List.of() : results.stream().map(AiAssembler::toToolDTO).toList());
        resultsFromTool(dto);
        return dto;
    }

    private static void resultsFromTool(CmsPageGenerateDTO dto) {
        if (dto.getTools() == null || dto.getTools().isEmpty()) {
            return;
        }
        AiToolCallDTO first = dto.getTools().get(0);
        if (first.getPayload() == null) {
            return;
        }
        dto.setTitle(text(first.getPayload().getOrDefault("title", dto.getTitle())));
        dto.setSummary(text(first.getPayload().getOrDefault("summary", dto.getSummary())));
        dto.setHtmlContent(text(first.getPayload().getOrDefault("htmlContent", dto.getHtmlContent())));
        dto.setCssContent(text(first.getPayload().getOrDefault("cssContent", dto.getCssContent())));
        dto.setBuilderProjectJson(text(first.getPayload().getOrDefault("builderProjectJson", dto.getBuilderProjectJson())));
        dto.setMarkdownContent(text(first.getPayload().getOrDefault("markdownContent", dto.getMarkdownContent())));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
