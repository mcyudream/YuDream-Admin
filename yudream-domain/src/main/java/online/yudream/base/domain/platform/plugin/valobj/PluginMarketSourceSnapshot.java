package online.yudream.base.domain.platform.plugin.valobj;

import java.time.LocalDateTime;
import java.util.List;

/** 一个市场源的目录快照，与 PluginMarketSource 一一对应。 */
public record PluginMarketSourceSnapshot(Long sourceId, LocalDateTime syncedAt, List<PluginStoreCatalogEntry> entries) {

    public PluginMarketSourceSnapshot {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
