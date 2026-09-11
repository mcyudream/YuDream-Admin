package online.yudream.base.domain.platform.plugin.repo;

import online.yudream.base.domain.platform.plugin.valobj.PluginMarketSourceSnapshot;

import java.util.List;
import java.util.Optional;

public interface PluginMarketSourceSnapshotRepo {

    /** 按 sourceId upsert：存在则覆盖条目并刷新 syncedAt。 */
    PluginMarketSourceSnapshot save(PluginMarketSourceSnapshot snapshot);

    Optional<PluginMarketSourceSnapshot> findBySourceId(Long sourceId);

    List<PluginMarketSourceSnapshot> findAll();

    void deleteBySourceId(Long sourceId);
}
