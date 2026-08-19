package online.yudream.base.domain.platform.wiki.repo;

import online.yudream.base.domain.platform.wiki.aggregate.WikiSource;

import java.util.List;
import java.util.Optional;

public interface WikiSourceRepo {
    WikiSource save(WikiSource source);

    Optional<WikiSource> findById(Long id);

    List<WikiSource> findBySpaceId(Long spaceId);

    List<WikiSource> findByIds(List<Long> ids);

    /** 按站内文件 ID 批量定位含对应抽取图片的资料，用于检索结果补充视觉模型 caption。 */
    List<WikiSource> findByImageFileObjectIds(Long spaceId, List<Long> fileObjectIds);

    Optional<WikiSource> findByContentHash(Long spaceId, String contentHash);

    List<WikiSource> searchByKeyword(Long spaceId, String keyword, int limit);

    void deleteById(Long id);

    long countBySpaceId(Long spaceId);
}
