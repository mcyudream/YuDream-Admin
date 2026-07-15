package online.yudream.base.infra.platform.wiki.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.wiki.service.WikiQueryExpansionGateway;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiWikiQueryExpansionGateway implements WikiQueryExpansionGateway {
    private final CapabilityModuleRepo capabilities;
    private final AiGenerationGateway generation;
    private final ObjectMapper objectMapper;

    @Override
    public List<String> expand(String providerCode, String modelCode, String query) {
        if (query == null || query.isBlank() || providerCode == null || providerCode.isBlank() || modelCode == null || modelCode.isBlank()) return List.of();
        try {
            Map<String, String> config = capabilities.findByCode("ai").filter(module -> module.enabled()).map(module -> module.getConfig()).orElse(Map.of());
            String content = generation.generate(new AiGenerationRequest("将用户问题扩展为最多三个适合向量检索的等义查询。只返回 JSON 字符串数组，不要解释。", query, null, providerCode, modelCode, config)).summary();
            List<String> values = objectMapper.readValue(stripFence(content), new TypeReference<>() {});
            return values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().limit(3).toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static String stripFence(String value) { return value == null ? "" : value.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", ""); }
}
