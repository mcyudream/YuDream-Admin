package online.yudream.base.domain.platform.plugin.repo;

import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;

import java.util.List;
import java.util.Optional;

public interface PluginModuleRepo {

    PluginModule save(PluginModule module);

    Optional<PluginModule> findByCode(String code);

    List<PluginModule> findAll();

    void deleteByCode(String code);
}
