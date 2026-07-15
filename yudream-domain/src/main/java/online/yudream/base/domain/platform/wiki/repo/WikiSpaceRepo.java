package online.yudream.base.domain.platform.wiki.repo;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace; import java.util.List; import java.util.Optional;
public interface WikiSpaceRepo { WikiSpace save(WikiSpace space); Optional<WikiSpace> findById(Long id); Optional<WikiSpace> findBySlug(String slug); List<WikiSpace> findAll(); void deleteById(Long id); }
