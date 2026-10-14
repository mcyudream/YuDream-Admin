package online.yudream.base.application.platform.wiki.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.ai.service.AiRunAppService;
import online.yudream.base.application.platform.ai.dto.AiRunDTO;
import online.yudream.base.domain.platform.ai.service.AiUsageExecutionScope;
import java.util.function.Consumer;
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
import org.springframework.beans.factory.ObjectProvider;
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
    private final ObjectProvider<AiGenerationGateway> aiGeneration;
    private final ObjectProvider<AiRunAppService> runs;
    private final WikiSpaceRepo spaceRepo;
    private final WikiNodeRepo nodeRepo;
    private final WikiIngestAppService ingestAppService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiRunDTO startPlan(Long userId, Long spaceId, String seed) {
        enabled();
        return runs.getObject().start(userId, "wiki-research", "wiki-research:" + spaceId, emitter -> {
            emitter.input(seed);
            try (AiUsageExecutionScope ignored = AiUsageExecutionScope.open(userId)) {
                return planStream(spaceId, seed,
                        delta -> emitter.event("ai.structured", "delta", Map.of("content", delta)),
                        emitter::reasoning);
            }
        }, null);
    }

    @Transactional(readOnly = true)
    public WikiResearchPlanDTO plan(Long spaceId, String seed) {
        return planStream(spaceId, seed, null, null);
    }

    private WikiResearchPlanDTO planStream(Long spaceId, String seed, Consumer<String> onDelta, Consumer<String> onReasoning) {
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
        AiGenerationResult result = aiGeneration.getObject().generateStream(request, onDelta, onReasoning, null, null);
        String text = result == null ? null : result.summary();
        if (text == null || text.isBlank()) {
            throw new BizException("AI 未返回研究计划，请重新生成");
        }
        try {
            JsonNode root = objectMapper.readTree(text);
            if (root == null || !root.isObject() || !root.path("topic").isTextual() || root.path("topic").asText().isBlank()
                    || !root.path("queries").isArray() || root.path("queries").isEmpty() || root.path("queries").size() > 20) {
                throw new BizException("AI 返回的研究计划缺少有效主题或查询，请重新生成");
            }
            for (JsonNode query : root.path("queries")) {
                if (!query.isTextual() || query.asText().isBlank()) throw new BizException("AI 返回的研究查询格式无效，请重新生成");
            }
            return new WikiResearchPlanDTO(root.path("topic").asText().trim(), root.path("rationale").asText(""), stringList(root, "queries"));
        }
        catch (BizException exception) { throw exception; }
        catch (Exception exception) {
            throw new BizException("AI 返回的研究计划无法解析，请重新生成");
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
