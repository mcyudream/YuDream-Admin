package online.yudream.base.domain.platform.cms.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.cms.aggregate.CmsPage;

import java.util.List;
import java.util.Optional;

public interface CmsPageRepo {

    CmsPage save(CmsPage page);

    Optional<CmsPage> findById(Long id);

    /** 按归属主题 + 路径查找；slug 只在同一主题内唯一，不同主题可复用同一 slug。 */
    Optional<CmsPage> findBySlug(String themeCode, String slug);

    List<CmsPage> findBySourcePluginCode(String pluginCode);

    void deleteById(Long id);

    /** 全部页面（数据迁移用）。 */
    List<CmsPage> findAll();

    PageResult<CmsPage> page(String themeCode, String keyword, int page, int size);

    PageResult<CmsPage> publishedPage(String themeCode, String keyword, String category, String tag, int page, int size);
}
