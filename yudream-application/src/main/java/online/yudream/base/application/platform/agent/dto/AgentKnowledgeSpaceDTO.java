package online.yudream.base.application.platform.agent.dto;

public record AgentKnowledgeSpaceDTO(
        String slug,
        String name,
        String embeddingProviderCode,
        String embeddingModelCode,
        int topK,
        boolean graphEnabled,
        boolean rerankEnabled
) {
}
