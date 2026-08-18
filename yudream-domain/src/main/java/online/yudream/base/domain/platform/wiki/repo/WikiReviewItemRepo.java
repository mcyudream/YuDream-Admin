package online.yudream.base.domain.platform.wiki.repo;

import online.yudream.base.domain.platform.wiki.aggregate.WikiReviewItem;
import online.yudream.base.domain.platform.wiki.enumerate.WikiReviewStatus;

import java.util.List;
import java.util.Optional;

public interface WikiReviewItemRepo {
    WikiReviewItem save(WikiReviewItem item);

    Optional<WikiReviewItem> findById(Long id);

    List<WikiReviewItem> findBySpaceId(Long spaceId);

    List<WikiReviewItem> findBySpaceIdAndStatus(Long spaceId, WikiReviewStatus status);

    void deleteById(Long id);
}
