package online.yudream.base.domain.platform.ai.valobj;

public record AiGenerationResult(
        String title,
        String summary,
        String htmlContent,
        String cssContent,
        String builderProjectJson,
        String markdownContent
) {
}
