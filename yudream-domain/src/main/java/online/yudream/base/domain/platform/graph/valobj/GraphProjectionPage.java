package online.yudream.base.domain.platform.graph.valobj;

import java.util.List;

public record GraphProjectionPage<T>(List<T> records, long total, int page, int size) {

    public GraphProjectionPage {
        records = records == null ? List.of() : List.copyOf(records);
        total = Math.max(total, 0);
        page = Math.max(page, 1);
        size = Math.max(size, 1);
    }
}
