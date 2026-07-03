package online.yudream.base.domain.system.setting.repo;

import online.yudream.base.domain.system.setting.aggregate.Setting;

import java.util.List;
import java.util.Optional;

/**
 * 系统设置仓库。
 */
public interface SettingRepo {

    Setting save(Setting setting);

    Optional<Setting> findByKey(String key);

    boolean existsByKey(String key);

    List<Setting> findByCategory(String category);

    List<Setting> findAll();
}
