package online.yudream.base.domain.platform.wiki.repo;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode; import java.util.List; import java.util.Optional;
public interface WikiNodeRepo { WikiNode save(WikiNode node); Optional<WikiNode> findById(Long id); List<WikiNode> findBySpaceId(Long spaceId); void deleteById(Long id); }
