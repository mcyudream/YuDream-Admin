package online.yudream.base.domain.platform.dataviz.valobj;

import java.util.Map;

public record ChartDatasetQuery(
        String source,
        String metric,
        Map<String, Object> params
) {
}
