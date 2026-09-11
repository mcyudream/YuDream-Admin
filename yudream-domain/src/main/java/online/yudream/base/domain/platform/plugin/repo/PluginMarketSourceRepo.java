package online.yudream.base.domain.platform.plugin.repo;

import online.yudream.base.domain.platform.plugin.aggregate.PluginMarketSource;

import java.util.List;
import java.util.Optional;

public interface PluginMarketSourceRepo {

    PluginMarketSource save(PluginMarketSource source);

    Optional<PluginMarketSource> findById(Long id);

    Optional<PluginMarketSource> findByCode(String code);

    /** 按 sortOrder 升序、id 升序返回，顺序即多源合并时的优先级。 */
    List<PluginMarketSource> findAll();

    void deleteById(Long id);
}
