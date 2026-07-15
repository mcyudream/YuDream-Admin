package online.yudream.base.application.platform.agent.dto;

public record AgentModelDTO(
        String providerCode,
        String providerName,
        String modelCode,
        String modelName,
        String kind,
        boolean vision,
        boolean configured,
        boolean defaultModel
) {
}
