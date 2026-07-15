package online.yudream.base.application.platform.agent.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.workflow.support.AgentKnowledgeOperations;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.wiki.service.WikiSearchAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.service.WikiEmbeddingGateway;
import online.yudream.base.domain.platform.wiki.service.WikiIndexGateway;
import online.yudream.base.domain.platform.wiki.service.WikiRerankGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiSearchHit;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentKnowledgeOperationsService implements AgentKnowledgeOperations {
    private final CapabilityAppService capabilities;
    private final WikiSearchAppService searches;
    private final WikiSpaceRepo spaces;
    private final WikiIndexGateway indexes;
    private final WikiRerankGateway reranks;
    private final WikiEmbeddingGateway embeddings;

    @Override
    public List<WikiSearchHit> search(String spaceSlug, String query, int topK, String pathPrefix, boolean graphExpansion) {
        return searches.searchForAdmin(spaceSlug, query, topK, pathPrefix, graphExpansion).stream()
                .map(hit -> new WikiSearchHit(hit.getScore(), parseId(hit.getNodeId()), hit.getTitle(), hit.getPath(), hit.getContent()))
                .toList();
    }

    @Override
    public List<WikiSearchHit> vectorSearch(String spaceSlug, String query, int topK, String pathPrefix, boolean graphExpansion) {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
        WikiSpace space = space(spaceSlug);
        return indexes.search(space, query, Math.clamp(topK, 1, 100), pathPrefix, graphExpansion && space.isGraphEnabled());
    }

    @Override
    public List<WikiSearchHit> rerank(
            String spaceSlug,
            String providerCode,
            String modelCode,
            String query,
            List<WikiSearchHit> candidates
    ) {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
        WikiSpace space = optionalSpace(spaceSlug);
        String provider = StringUtils.hasText(providerCode) ? providerCode : space == null ? "" : space.getEmbeddingProviderCode();
        return reranks.rerank(provider, modelCode, query, candidates);
    }

    @Override
    public List<List<Float>> embedding(
            String spaceSlug,
            String providerCode,
            String modelCode,
            List<String> texts
    ) {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
        WikiSpace space = optionalSpace(spaceSlug);
        String provider = StringUtils.hasText(providerCode) ? providerCode : space == null ? "" : space.getEmbeddingProviderCode();
        String model = StringUtils.hasText(modelCode) ? modelCode : space == null ? "" : space.getEmbeddingModelCode();
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(model)) {
            throw new BizException("Embedding 节点必须选择 Provider 和模型");
        }
        return embeddings.embed(provider, model, texts);
    }

    private WikiSpace space(String slug) {
        if (!StringUtils.hasText(slug)) {
            throw new BizException("知识节点必须选择知识空间");
        }
        return spaces.findBySlug(slug).orElseThrow(() -> new BizException("知识空间不存在：" + slug));
    }

    private WikiSpace optionalSpace(String slug) {
        return StringUtils.hasText(slug) ? space(slug) : null;
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
