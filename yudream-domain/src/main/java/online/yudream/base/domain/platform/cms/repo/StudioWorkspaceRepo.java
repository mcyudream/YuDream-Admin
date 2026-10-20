package online.yudream.base.domain.platform.cms.repo;

import online.yudream.base.domain.platform.cms.aggregate.StudioWorkspace;
import java.util.Optional;

public interface StudioWorkspaceRepo {
    Optional<StudioWorkspace> findByThemeCode(String themeCode);
    /** 原子插入或按旧revision替换整份聚合；冲突返回false，禁止自动重试。 */
    boolean compareAndSet(long expectedRevision, StudioWorkspace workspace);
}
