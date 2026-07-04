package online.yudream.base.domain.platform.cms.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.cms.aggregate.CmsPage;

import java.util.Optional;

public interface CmsPageRepo {

    CmsPage save(CmsPage page);

    Optional<CmsPage> findById(Long id);

    Optional<CmsPage> findBySlug(String slug);

    PageResult<CmsPage> page(String keyword, int page, int size);

    PageResult<CmsPage> publishedPage(String keyword, String category, String tag, int page, int size);
}
