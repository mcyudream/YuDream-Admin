package online.yudream.base.plugin.spi.system.graph;

import java.util.List;

/** A bounded page returned from a host-managed graph projection. */
public record PluginGraphProjectionPage<T>(List<T> records, long total, int page, int size) {

    public PluginGraphProjectionPage {
        records = records == null ? List.of() : List.copyOf(records);
        total = Math.max(total, 0);
        page = Math.max(page, 1);
        size = Math.max(size, 1);
    }

    public static <T> PluginGraphProjectionPage<T> empty(int page, int size) {
        return new PluginGraphProjectionPage<>(List.of(), 0, page, size);
    }
}
