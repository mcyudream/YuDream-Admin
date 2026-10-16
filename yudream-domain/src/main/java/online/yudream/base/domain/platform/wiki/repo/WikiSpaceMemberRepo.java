package online.yudream.base.domain.platform.wiki.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpaceMember;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceMemberSubjectType;

import java.util.List;
import java.util.Optional;

/**
 * 空间成员授权仓储。
 */
public interface WikiSpaceMemberRepo {

    WikiSpaceMember save(WikiSpaceMember member);

    Optional<WikiSpaceMember> findById(Long id);

    Optional<WikiSpaceMember> findBySubject(Long spaceId, WikiSpaceMemberSubjectType subjectType, Long subjectId);

    List<WikiSpaceMember> findBySpaceId(Long spaceId);

    PageResult<WikiSpaceMember> page(Long spaceId, int page, int size);

    long countBySpaceId(Long spaceId);

    void deleteById(Long id);

    void deleteBySpaceId(Long spaceId);
}
