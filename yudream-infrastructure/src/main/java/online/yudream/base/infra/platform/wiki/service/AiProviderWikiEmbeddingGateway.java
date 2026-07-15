package online.yudream.base.infra.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.wiki.service.WikiEmbeddingGateway;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderConfigParser;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderEndpoint;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiProviderWikiEmbeddingGateway implements WikiEmbeddingGateway {

    private final CapabilityModuleRepo capabilityModuleRepo;
    private final AiProviderConfigParser providerConfigParser;

    @Override
    @SuppressWarnings("unchecked")
    public List<List<Float>> embed(String providerCode, String modelCode, List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        Map<String, String> config = capabilityModuleRepo.findByCode("ai")
                .filter(module -> module.enabled())
                .map(module -> module.getConfig())
                .orElseThrow(() -> new BizException("AI 能力未启用"));
        AiProviderEndpoint provider = providerConfigParser.parse(config).stream()
                .filter(AiProviderEndpoint::enabled)
                .filter(item -> item.code().equalsIgnoreCase(providerCode))
                .findFirst().orElseThrow(() -> new BizException("Embedding Provider 不存在或未启用"));
        if (!provider.embeddingModels().contains(modelCode)) {
            throw new BizException("所选模型不是该 Provider 的 Embedding 模型");
        }
        if (!StringUtils.hasText(provider.apiKey())) throw new BizException("Embedding Provider 未配置 API Key");
        Map<String, Object> response = RestClient.create().post()
                .uri(provider.endpointBaseUrl() + "/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + provider.apiKey())
                .body(Map.of("model", modelCode, "input", texts))
                .retrieve().body(Map.class);
        Object data = response == null ? null : response.get("data");
        if (!(data instanceof List<?> rows) || rows.size() != texts.size()) throw new BizException("Embedding 响应格式无效");
        return rows.stream().map(row -> {
            Object embedding = row instanceof Map<?, ?> map ? map.get("embedding") : null;
            if (!(embedding instanceof List<?> values) || values.isEmpty()) throw new BizException("Embedding 响应缺少向量");
            return values.stream().map(value -> ((Number) value).floatValue()).toList();
        }).toList();
    }
}
