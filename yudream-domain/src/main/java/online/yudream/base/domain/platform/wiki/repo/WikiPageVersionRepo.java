package online.yudream.base.domain.platform.wiki.repo;
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion; import java.util.Optional;
public interface WikiPageVersionRepo { WikiPageVersion save(WikiPageVersion version); Optional<WikiPageVersion> findById(Long id); Optional<WikiPageVersion> findLatest(Long nodeId); }
