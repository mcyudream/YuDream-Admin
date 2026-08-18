package online.yudream.base.domain.platform.wiki.repo;

import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiPageType;

import java.util.List;
import java.util.Optional;

public interface WikiNodeRepo {
    WikiNode save(WikiNode node);

    Optional<WikiNode> findById(Long id);

    List<WikiNode> findBySpaceId(Long spaceId);

    List<WikiNode> searchByKeyword(Long spaceId, String keyword, int limit);

    Optional<WikiNode> findBySlug(Long spaceId, String slug);

    List<WikiNode> findByType(Long spaceId, WikiPageType pageType);

    void deleteById(Long id);
}
