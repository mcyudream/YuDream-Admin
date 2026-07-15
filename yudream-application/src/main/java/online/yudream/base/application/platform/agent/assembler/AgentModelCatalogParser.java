package online.yudream.base.application.platform.agent.assembler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.dto.AgentModelDTO;
import online.yudream.base.domain.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AgentModelCatalogParser {
    private final ObjectMapper objectMapper;

    public List<AgentModelDTO> parse(Map<String, String> config) {
        String providersJson = config == null ? null : config.get("providers");
        if (!StringUtils.hasText(providersJson)) {
            return legacyModels(config);
        }
        try {
            List<AgentModelDTO> result = new ArrayList<>();
            for (JsonNode provider : objectMapper.readTree(providersJson)) {
                if (!provider.path("enabled").asBoolean(true)) {
                    continue;
                }
                String providerCode = provider.path("code").asText().trim();
                if (!StringUtils.hasText(providerCode)) {
                    continue;
                }
                String providerName = provider.path("name").asText(providerCode);
                String defaultModel = provider.path("defaultModel").asText();
                boolean configured = StringUtils.hasText(provider.path("apiKey").asText());
                append(result, provider.path("models"), providerCode, providerName, "chat", defaultModel, configured);
                append(result, provider.path("embeddingModels"), providerCode, providerName, "embedding", defaultModel, configured);
                append(result, provider.path("rerankModels"), providerCode, providerName, "rerank", defaultModel, configured);
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            throw new BizException("AI 模型配置格式无效");
        }
    }

    private void append(
            List<AgentModelDTO> target,
            JsonNode models,
            String providerCode,
            String providerName,
            String kind,
            String defaultModel,
            boolean configured
    ) {
        if (!models.isArray()) {
            return;
        }
        for (JsonNode model : models) {
            String code = model.isTextual()
                    ? model.asText().trim()
                    : firstText(model.path("code").asText(), model.path("model").asText(), model.path("name").asText());
            if (!StringUtils.hasText(code)) {
                continue;
            }
            String name = model.isTextual() ? code : model.path("name").asText(code);
            String modelKind = model.isTextual()
                    ? kind
                    : normalizedKind(model.path("kind").asText(), kind);
            boolean vision = "chat".equals(modelKind) && !model.isTextual() && model.path("vision").asBoolean(false);
            target.add(new AgentModelDTO(
                    providerCode,
                    providerName,
                    code,
                    name,
                    modelKind,
                    vision,
                    configured,
                    code.equals(defaultModel)
            ));
        }
    }

    private String normalizedKind(String configuredKind, String fallback) {
        String kind = StringUtils.hasText(configuredKind)
                ? configuredKind.trim().toLowerCase()
                : fallback;
        return List.of("chat", "embedding", "rerank").contains(kind) ? kind : fallback;
    }

    private List<AgentModelDTO> legacyModels(Map<String, String> config) {
        if (config == null) {
            return List.of();
        }
        String modelCode = firstText(config.get("model"), config.get("defaultModel"));
        if (!StringUtils.hasText(modelCode)) {
            return List.of();
        }
        String providerCode = firstText(config.get("providerCode"), "default");
        String providerName = firstText(config.get("providerName"), "Default AI");
        return List.of(new AgentModelDTO(
                providerCode,
                providerName,
                modelCode,
                modelCode,
                "chat",
                false,
                StringUtils.hasText(config.get("apiKey")),
                true
        ));
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
