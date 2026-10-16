package online.yudream.base.domain.platform.wiki.repo;

import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiPageType;
import online.yudream.base.domain.platform.wiki.valobj.WikiSpaceStats;

import java.util.List;
import java.util.Optional;

public interface WikiNodeRepo {
    WikiNode save(WikiNode node);

    Optional<WikiNode> findById(Long id);

    List<WikiNode> findBySpaceId(Long spaceId);

    /** 空间节点聚合（列表卡片用）：只读投影，不加载节点正文。 */
    WikiSpaceStats statsBySpaceId(Long spaceId);

    List<WikiNode> searchByKeyword(Long spaceId, String keyword, int limit);

    Optional<WikiNode> findBySlug(Long spaceId, String slug);

    List<WikiNode> findByType(Long spaceId, WikiPageType pageType);

    void deleteById(Long id);
}
