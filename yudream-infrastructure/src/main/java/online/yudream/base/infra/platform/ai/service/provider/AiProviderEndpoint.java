package online.yudream.base.infra.platform.ai.service.provider;

import org.springframework.util.StringUtils;

import java.util.List;

public record AiProviderEndpoint(
        String code,
        String name,
        AiProviderType type,
        String baseUrl,
        String completionsPath,
        String apiKey,
        String proxyUrl,
        String defaultModel,
        String temperature,
        String extraBody,
        List<AiModelEndpoint> models,
        List<String> embeddingModels,
        List<String> rerankModels,
        boolean enabled
) {
    public static final String DEFAULT_COMPLETIONS_PATH = "/chat/completions";

    public AiProviderEndpoint {
        type = type == null ? AiProviderType.OPENAI_COMPATIBLE : type;
        completionsPath = StringUtils.hasText(completionsPath) ? "/" + completionsPath.trim().replaceAll("^/+", "") : DEFAULT_COMPLETIONS_PATH;
        models = models == null ? List.of() : List.copyOf(models);
        embeddingModels = embeddingModels == null ? List.of() : List.copyOf(embeddingModels);
        rerankModels = rerankModels == null ? List.of() : List.copyOf(rerankModels);
    }

    public String endpointBaseUrl() {
        String value = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "";
        String suffix = completionsPath.replaceAll("^/+", "/");
        value = value.replaceAll("/+$", "");
        if (value.endsWith(suffix)) {
            return value.substring(0, value.length() - suffix.length());
        }
        return value;
    }

    public String endpointUrl() {
        return endpointBaseUrl() + completionsPath;
    }

    public String displayName() {
        return StringUtils.hasText(name) ? name.trim() : code;
    }
}
