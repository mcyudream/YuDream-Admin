package online.yudream.base.domain.platform.cms.repo;

import online.yudream.base.domain.platform.cms.aggregate.StudioBlockTemplate;

import java.util.List;
import java.util.Optional;

public interface StudioBlockTemplateRepo {
    List<StudioBlockTemplate> findAll();
    Optional<StudioBlockTemplate> findById(String id);
    long count();
    void insert(StudioBlockTemplate template);
    /** 按旧版本原子替换；版本冲突返回 false，禁止自动重试。 */
    boolean compareAndSet(long expectedRevision, StudioBlockTemplate template);
    boolean delete(String id);
}
