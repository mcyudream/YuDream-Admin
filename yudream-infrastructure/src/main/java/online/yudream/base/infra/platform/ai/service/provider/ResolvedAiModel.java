package online.yudream.base.infra.platform.ai.service.provider;

public record ResolvedAiModel(
        AiProviderEndpoint provider,
        AiModelEndpoint model,
        AiProviderAdapter adapter
) {
}
