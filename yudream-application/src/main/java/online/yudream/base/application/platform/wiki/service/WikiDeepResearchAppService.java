package online.yudream.base.application.platform.wiki.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.wiki.dto.WikiResearchPlanDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;
import online.yudream.base.domain.platform.ai.valobj.AiStructuredOutput;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 深度研究：LLM 生成研究主题与查询，可编辑确认后启动网络搜索并合成研究页面。
 */
@Service
@RequiredArgsConstructor
public class WikiDeepResearchAppService {

    private final CapabilityAppService capabilities;
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final AiGenerationGateway aiGeneration;
    private final WikiSpaceRepo spaceRepo;
    private final WikiNodeRepo nodeRepo;
    private final WikiIngestAppService ingestAppService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public WikiResearchPlanDTO plan(Long spaceId, String seed) {
        enabled();
        WikiSpace space = spaceRepo.findById(spaceId).orElseThrow(() -> new BizException("知识库不存在"));
        String overview = nodeRepo.findBySlug(spaceId, "overview").map(WikiNode::bodyMarkdown).orElse("");
        String system = "你是深度研究主题生成器。只输出 JSON，不输出解释。";
        String user = "知识库目的：\n" + blankToEmpty(space.getPurpose()) + "\n\n"
                + "全局概要：\n" + overview + "\n\n"
                + "研究起点：" + (seed == null ? "" : seed) + "\n\n"
                + "请生成领域精准的研究主题与查询，输出如下 JSON：\n"
                + "{\"topic\":\"研究主题\",\"rationale\":\"理由\",\"queries\":[\"针对搜索引擎优化的查询\"]}";
        AiGenerationRequest request = new AiGenerationRequest(system, user, null,
                space.getIngestProviderCode(), space.getIngestModelCode(), aiConfig())
                .withStructuredOutput(AiStructuredOutput.jsonObject());
        AiGenerationResult result = aiGeneration.generate(request);
        String text = result == null ? null : result.summary();
        if (text == null || text.isBlank()) {
            return new WikiResearchPlanDTO(seed == null ? "未命名研究" : seed, "", List.of(seed == null ? "" : seed));
        }
        try {
            JsonNode root = objectMapper.readTree(stripFence(text));
            return new WikiResearchPlanDTO(root.path("topic").asText(seed),
                    root.path("rationale").asText(""), stringList(root, "queries"));
        }
        catch (Exception exception) {
            return new WikiResearchPlanDTO(seed == null ? "未命名研究" : seed, "",
                    List.of(seed == null ? "" : seed));
        }
    }

    @Transactional
    public void start(Long spaceId, String topic, List<String> queries) {
        enabled();
        if (topic == null || topic.isBlank()) {
            throw new BizException("研究主题不能为空");
        }
        ingestAppService.enqueueResearch(spaceId, topic, queries == null ? List.of(topic) : queries);
    }

    private List<String> stringList(JsonNode node, String field) {
        JsonNode array = node.path(field);
        List<String> list = new ArrayList<>();
        if (array.isArray()) {
            array.forEach(item -> {
                String value = item.asText("").trim();
                if (!value.isBlank()) {
                    list.add(value);
                }
            });
        }
        return list;
    }

    private String stripFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            String withoutOpen = firstNewline >= 0 ? trimmed.substring(firstNewline + 1) : "";
            int close = withoutOpen.lastIndexOf("```");
            if (close >= 0) {
                withoutOpen = withoutOpen.substring(0, close);
            }
            return withoutOpen.trim();
        }
        return trimmed;
    }

    private Map<String, String> aiConfig() {
        return capabilityModuleRepo.findByCode("ai")
                .filter(CapabilityModule::enabled)
                .map(CapabilityModule::getConfig)
                .orElseThrow(() -> new BizException("AI 能力未启用"));
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void enabled() {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
    }
}
