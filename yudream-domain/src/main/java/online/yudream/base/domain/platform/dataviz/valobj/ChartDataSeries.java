package online.yudream.base.domain.platform.dataviz.valobj;

import java.util.List;

/**
 * 图表数据序列，不同类型的图表使用不同的字段组合：
 * <ul>
 *     <li>柱状图 / 折线图 / 饼图 / 散点图 / KPI：name + categories + values</li>
 *     <li>关系图 / 桑基图：name + nodes + links</li>
 * </ul>
 */
public record ChartDataSeries(
        String name,
        List<String> categories,
        List<Number> values,
        List<ChartDataNode> nodes,
        List<ChartDataLink> links
) {
}
