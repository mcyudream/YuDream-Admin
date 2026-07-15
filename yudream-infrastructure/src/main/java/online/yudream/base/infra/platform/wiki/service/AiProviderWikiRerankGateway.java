package online.yudream.base.infra.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.wiki.service.WikiRerankGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiSearchHit;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderConfigParser;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderEndpoint;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiProviderWikiRerankGateway implements WikiRerankGateway {
    private final CapabilityModuleRepo capabilities;
    private final AiProviderConfigParser providers;

    @Override
    @SuppressWarnings("unchecked")
    public List<WikiSearchHit> rerank(String providerCode, String query, List<WikiSearchHit> candidates) {
        return rerank(providerCode, null, query, candidates);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<WikiSearchHit> rerank(String providerCode, String modelCode, String query, List<WikiSearchHit> candidates) {
        if (candidates == null || candidates.size() < 2 || !StringUtils.hasText(providerCode)) return candidates;
        try {
            Map<String, String> config = capabilities.findByCode("ai").filter(module -> module.enabled()).map(module -> module.getConfig()).orElse(Map.of());
            AiProviderEndpoint provider = providers.parse(config).stream().filter(AiProviderEndpoint::enabled).filter(item -> item.code().equalsIgnoreCase(providerCode)).findFirst().orElse(null);
            if (provider == null || provider.rerankModels().isEmpty() || !StringUtils.hasText(provider.apiKey())) return candidates;
            String selectedModel = StringUtils.hasText(modelCode) ? modelCode : provider.rerankModels().getFirst();
            if (!provider.rerankModels().contains(selectedModel)) return candidates;
            Map<String, Object> response = RestClient.create().post().uri(provider.endpointBaseUrl() + "/rerank")
                    .contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + provider.apiKey())
                    .body(Map.of("model", selectedModel, "query", query, "documents", candidates.stream().map(WikiSearchHit::content).toList(), "top_n", candidates.size()))
                    .retrieve().body(Map.class);
            Object raw = response == null ? null : response.get("results");
            if (!(raw instanceof List<?> rows)) return candidates;
            List<WikiSearchHit> ranked = new ArrayList<>();
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> value) || !(value.get("index") instanceof Number index) || index.intValue() < 0 || index.intValue() >= candidates.size()) continue;
                Object rawScore = value.containsKey("relevance_score") ? value.get("relevance_score") : value.get("score");
                double score = rawScore instanceof Number number ? number.doubleValue() : candidates.get(index.intValue()).score();
                WikiSearchHit hit = candidates.get(index.intValue()); ranked.add(new WikiSearchHit(score, hit.nodeId(), hit.title(), hit.path(), hit.content()));
            }
            return ranked.isEmpty() ? candidates : ranked;
        } catch (Exception ignored) { return candidates; }
    }
}
