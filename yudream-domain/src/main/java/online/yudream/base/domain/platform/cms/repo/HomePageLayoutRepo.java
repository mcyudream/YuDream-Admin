package online.yudream.base.domain.platform.cms.repo;

import online.yudream.base.domain.platform.cms.aggregate.HomePageLayout;

import java.util.List;
import java.util.Optional;

public interface HomePageLayoutRepo {

    HomePageLayout save(HomePageLayout layout);

    /** 指定主题的首页布局；每个主题各持有一套，彼此独立。 */
    Optional<HomePageLayout> findByThemeCode(String themeCode);

    /** 全部首页布局（数据迁移与管理端主题选择器用）。 */
    List<HomePageLayout> findAll();

    void deleteById(Long id);
}
