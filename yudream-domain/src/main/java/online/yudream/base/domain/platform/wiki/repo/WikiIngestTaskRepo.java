package online.yudream.base.domain.platform.wiki.repo;

import online.yudream.base.domain.platform.wiki.aggregate.WikiIngestTask;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIngestTaskStatus;

import java.util.List;
import java.util.Optional;

public interface WikiIngestTaskRepo {
    WikiIngestTask save(WikiIngestTask task);

    Optional<WikiIngestTask> findById(Long id);

    Optional<WikiIngestTask> findNextQueued();

    List<WikiIngestTask> findBySpaceId(Long spaceId);

    List<WikiIngestTask> findByStatus(WikiIngestTaskStatus status);

    void deleteById(Long id);
}
