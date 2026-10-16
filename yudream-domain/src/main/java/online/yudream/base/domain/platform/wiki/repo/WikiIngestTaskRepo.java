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

    /** 空间内指定状态的任务数（列表卡片提示用），不加载任务实体。 */
    long countBySpaceIdAndStatus(Long spaceId, WikiIngestTaskStatus status);

    void deleteById(Long id);
}
