package online.yudream.base.application.platform.agent.workflow.support;

import online.yudream.base.domain.platform.wiki.valobj.WikiSearchHit;

import java.util.List;

public interface AgentKnowledgeOperations {
    List<WikiSearchHit> search(String spaceSlug, String query, int topK, String pathPrefix, boolean graphExpansion);

    List<WikiSearchHit> vectorSearch(String spaceSlug, String query, int topK, String pathPrefix, boolean graphExpansion);

    List<WikiSearchHit> rerank(
            String spaceSlug,
            String providerCode,
            String modelCode,
            String query,
            List<WikiSearchHit> candidates
    );

    List<List<Float>> embedding(
            String spaceSlug,
            String providerCode,
            String modelCode,
            List<String> texts
    );
}
