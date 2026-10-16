package online.yudream.base.domain.platform.wiki.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.wiki.aggregate.WikiComment;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCommentStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 页面评论仓储。评论为独立集合，分页查询避免按空间全量拉取。
 */
public interface WikiCommentRepo {

    WikiComment save(WikiComment comment);

    Optional<WikiComment> findById(Long id);

    /** 治理列表：按空间与（可选）状态分页，倒序。 */
    PageResult<WikiComment> pageBySpace(Long spaceId, WikiCommentStatus status, int page, int size);

    /** 公开端：按空间与页面分页取已发布根评论（置顶优先）。 */
    PageResult<WikiComment> pagePublishedRoots(Long spaceId, Long nodeId, int page, int size);

    /** 公开端：批量取指定根评论下的已发布回复（同页浅线程）。 */
    List<WikiComment> findPublishedReplies(Long spaceId, Long nodeId, Collection<Long> parentIds);

    long countBySpaceId(Long spaceId);

    void deleteBySpaceId(Long spaceId);
}
