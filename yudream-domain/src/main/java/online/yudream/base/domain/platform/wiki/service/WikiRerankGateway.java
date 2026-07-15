package online.yudream.base.domain.platform.wiki.service;

import online.yudream.base.domain.platform.wiki.valobj.WikiSearchHit;

import java.util.List;

public interface WikiRerankGateway {
    List<WikiSearchHit> rerank(String providerCode, String query, List<WikiSearchHit> candidates);

    default List<WikiSearchHit> rerank(
            String providerCode,
            String modelCode,
            String query,
            List<WikiSearchHit> candidates
    ) {
        return rerank(providerCode, query, candidates);
    }
}
