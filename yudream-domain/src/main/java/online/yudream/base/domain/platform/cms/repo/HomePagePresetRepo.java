package online.yudream.base.domain.platform.cms.repo;

import online.yudream.base.domain.platform.cms.aggregate.HomePagePreset;
import online.yudream.base.domain.platform.cms.enumerate.HomePagePresetSource;

import java.util.List;
import java.util.Optional;

public interface HomePagePresetRepo {

    HomePagePreset save(HomePagePreset preset);

    Optional<HomePagePreset> findByCode(String code);

    /** 全量方案，按创建时间倒序。 */
    List<HomePagePreset> findAll();

    /** 指定主题的方案，按创建时间倒序。 */
    List<HomePagePreset> findByThemeCode(String themeCode);

    /** 指定来源的方案，按创建时间倒序。 */
    List<HomePagePreset> findBySource(HomePagePresetSource source);

    void deleteByCode(String code);
}
