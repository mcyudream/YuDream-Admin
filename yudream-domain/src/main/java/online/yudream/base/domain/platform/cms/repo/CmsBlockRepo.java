package online.yudream.base.domain.platform.cms.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.cms.aggregate.CmsBlock;
import online.yudream.base.domain.platform.cms.enumerate.CmsBlockKind;

import java.util.List;
import java.util.Optional;

public interface CmsBlockRepo {

    CmsBlock save(CmsBlock block);

    Optional<CmsBlock> findById(Long id);

    Optional<CmsBlock> findByCode(String code);

    void deleteById(Long id);

    PageResult<CmsBlock> page(String keyword, String category, CmsBlockKind kind, int page, int size);

    List<CmsBlock> findEnabledByKind(CmsBlockKind kind);

    List<CmsBlock> findAllEnabled();
}
