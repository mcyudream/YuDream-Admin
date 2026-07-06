package online.yudream.base.domain.platform.dataviz.valobj;

public record ChartDataNode(
        String id,
        String name,
        String group,
        Number value
) {
}
