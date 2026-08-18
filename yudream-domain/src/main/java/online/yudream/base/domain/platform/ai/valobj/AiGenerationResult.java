package online.yudream.base.domain.platform.ai.valobj;

import java.util.List;

public record AiGenerationResult(
        String title,
        String summary,
        String htmlContent,
        String cssContent,
        String jsContent,
        String builderProjectJson,
        String markdownContent,
        List<AiAgentToolCall> toolCalls,
        List<AiAgentToolResult> toolResults,
        AiUsage usage
) {

    public AiGenerationResult {
        usage = usage == null ? AiUsage.empty() : usage;
    }

    public AiGenerationResult(
            String title,
            String summary,
            String htmlContent,
            String cssContent,
            String jsContent,
            String builderProjectJson,
            String markdownContent,
            List<AiAgentToolCall> toolCalls,
            List<AiAgentToolResult> toolResults
    ) {
        this(title, summary, htmlContent, cssContent, jsContent, builderProjectJson, markdownContent,
                toolCalls, toolResults, AiUsage.empty());
    }

    public static AiGenerationResult of(String summary, List<AiAgentToolResult> toolResults, AiUsage usage) {
        return new AiGenerationResult("", summary, "", "", "", "", "", List.of(), toolResults == null ? List.of() : toolResults, usage);
    }
}
