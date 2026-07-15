package online.yudream.base.infra.platform.wiki.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.wiki.service.WikiGraphExtractionGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiGraphRelation;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiWikiGraphExtractionGateway implements WikiGraphExtractionGateway {
    private static final String PROMPT = """
            从给定 Markdown 中抽取可用于知识检索的事实关系。只返回 JSON 数组，不要 Markdown，不要解释。
            每项字段：source, sourceType, relation, target, targetType, confidence(0 到 1)。
            只保留内容明确支持的关系，实体名称不超过 80 个字符，最多 50 项。
            """;
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final AiGenerationGateway generationGateway;
    private final ObjectMapper objectMapper;

    @Override
    public List<WikiGraphRelation> extract(String providerCode, String modelCode, String title, String markdown) {
        Map<String, String> config = capabilityModuleRepo.findByCode("ai")
                .filter(module -> module.enabled()).map(module -> module.getConfig())
                .orElseThrow(() -> new BizException("AI 能力未启用"));
        String content = generationGateway.generate(new AiGenerationRequest(PROMPT,
                "标题：" + title + "\n\n" + markdown, null, providerCode, modelCode, config)).summary();
        try {
            String json = stripFence(content);
            List<WikiGraphRelation> relations = objectMapper.readValue(json, new TypeReference<>() {});
            return relations.stream().filter(this::valid).limit(50).toList();
        } catch (Exception e) {
            throw new BizException("图谱抽取结果无效：" + e.getMessage());
        }
    }

    private boolean valid(WikiGraphRelation value) {
        return value != null && text(value.source()) && text(value.target()) && text(value.relation())
                && value.source().length() <= 80 && value.target().length() <= 80
                && value.confidence() >= 0 && value.confidence() <= 1;
    }

    private static boolean text(String value) { return value != null && !value.isBlank(); }
    private static String stripFence(String value) { return value == null ? "" : value.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", ""); }
}
