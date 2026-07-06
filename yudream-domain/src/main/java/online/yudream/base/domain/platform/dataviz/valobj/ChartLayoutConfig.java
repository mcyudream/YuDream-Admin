package online.yudream.base.domain.platform.dataviz.valobj;

import java.util.Map;

public record ChartLayoutConfig(
        String title,
        String subTitle,
        String theme,
        Map<String, Object> extras
) {
}
