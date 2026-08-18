package online.yudream.base.domain.platform.wiki.repo;

import online.yudream.base.domain.platform.wiki.aggregate.WikiSource;

import java.util.List;
import java.util.Optional;

public interface WikiSourceRepo {
    WikiSource save(WikiSource source);

    Optional<WikiSource> findById(Long id);

    List<WikiSource> findBySpaceId(Long spaceId);

    List<WikiSource> findByIds(List<Long> ids);

    Optional<WikiSource> findByContentHash(Long spaceId, String contentHash);

    List<WikiSource> searchByKeyword(Long spaceId, String keyword, int limit);

    void deleteById(Long id);

    long countBySpaceId(Long spaceId);
}
