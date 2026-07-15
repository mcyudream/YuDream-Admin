package online.yudream.base.application.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.wiki.dto.WikiSearchHitDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.service.WikiIndexGateway;
import online.yudream.base.domain.platform.wiki.service.WikiQueryExpansionGateway;
import online.yudream.base.domain.platform.wiki.service.WikiRerankGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WikiSearchAppService {
    private final CapabilityAppService capabilities;
    private final WikiSpaceRepo spaces;
    private final WikiIndexGateway indexes;
    private final WikiQueryExpansionGateway expansions;
    private final WikiRerankGateway reranks;

    @Transactional(readOnly = true)
    public List<WikiSearchHitDTO> search(String slug, String query, int topK, String prefix, boolean graph) {
        return search(slug, query, topK, prefix, graph, true);
    }

    @Transactional(readOnly = true)
    public List<WikiSearchHitDTO> searchForAdmin(String slug, String query, int topK, String prefix, boolean graph) {
        return search(slug, query, topK, prefix, graph, false);
    }

    @Transactional(readOnly = true)
    public List<WikiSearchHitDTO> searchForPublicSite(String slug, String query, int topK, String prefix, boolean graph) {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
        WikiSpace space = spaces.findBySlug(slug).orElseThrow(() -> new BizException("知识库不存在"));
        if (!space.isPublicReadEnabled()) throw new BizException("该知识库未开放公开阅读");
        return search(space, query, topK, prefix, graph);
    }

    private List<WikiSearchHitDTO> search(WikiSpace space, String query, int topK, String prefix, boolean graph) {
        List<String> queries = new java.util.ArrayList<>(List.of(query));
        if (space.isQueryExpansionEnabled()) queries.addAll(expansions.expand(space.getGraphProviderCode(), space.getGraphModelCode(), query));
        List<online.yudream.base.domain.platform.wiki.valobj.WikiSearchHit> candidates = queries.stream().flatMap(item -> indexes.search(space, item, Math.clamp(topK * 2, 4, 30), prefix, graph && space.isGraphEnabled()).stream())
                .collect(java.util.stream.Collectors.toMap(hit -> hit.nodeId() + ":" + hit.path(), java.util.function.Function.identity(), (left, right) -> left.score() >= right.score() ? left : right))
                .values().stream().sorted(java.util.Comparator.comparingDouble(online.yudream.base.domain.platform.wiki.valobj.WikiSearchHit::score).reversed()).toList();
        List<online.yudream.base.domain.platform.wiki.valobj.WikiSearchHit> ranked = space.isRerankEnabled()
                ? reranks.rerank(space.getEmbeddingProviderCode(), query, candidates)
                : candidates;
        return ranked.stream().limit(topK)
                .map(hit -> WikiSearchHitDTO.builder().score(hit.score()).nodeId(String.valueOf(hit.nodeId())).title(hit.title())
                        .path(hit.path()).content(hit.content()).sourceUrl(sourceUrl(space, hit.path())).build())
                .toList();
    }

    private List<WikiSearchHitDTO> search(String slug, String query, int topK, String prefix, boolean graph, boolean requireExternal) {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
        WikiSpace space = spaces.findBySlug(slug).orElseThrow(() -> new BizException("知识库不存在"));
        if (requireExternal && !space.isExternalSearchEnabled()) throw new BizException("该知识库未开放外部检索");
        return search(space, query, topK, prefix, graph);
    }

    private static String sourceUrl(WikiSpace space, String path) {
        return "/wiki/" + space.getSlug() + "/" + (path == null ? "" : path.replaceFirst("^/+", ""));
    }
}
