package online.yudream.base.domain.platform.wiki.repo;

import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WikiPageVersionRepo {
    WikiPageVersion save(WikiPageVersion version);

    Optional<WikiPageVersion> findById(Long id);

    List<WikiPageVersion> findByIds(Collection<Long> ids);

    Optional<WikiPageVersion> findLatest(Long nodeId);
}
