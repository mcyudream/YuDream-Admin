package online.yudream.base.domain.platform.ai.valobj;

import java.util.List;

public record AiGenerationResult(
        String title,
        String summary,
        String htmlContent,
        String cssContent,
        String builderProjectJson,
        String markdownContent,
        List<AiAgentToolCall> toolCalls,
        List<AiAgentToolResult> toolResults
) {
}
