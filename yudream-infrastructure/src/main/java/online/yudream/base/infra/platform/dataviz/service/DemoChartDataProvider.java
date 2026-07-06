package online.yudream.base.infra.platform.dataviz.service;

import online.yudream.base.domain.platform.dataviz.valobj.ChartDataLink;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDataNode;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDataSeries;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DemoChartDataProvider implements ChartDataProvider {

    @Override
    public String source() {
        return "demo";
    }

    @Override
    public List<ChartDataSeries> query(ChartDatasetQuery query) {
        if ("graph".equals(query.metric())) {
            return List.of(buildGraphSeries());
        }
        return List.of(buildTrendSeries());
    }

    private ChartDataSeries buildGraphSeries() {
        return new ChartDataSeries(
                "demo-graph",
                null,
                null,
                List.of(
                        new ChartDataNode("1", "节点 1", "A", 10),
                        new ChartDataNode("2", "节点 2", "A", 20),
                        new ChartDataNode("3", "节点 3", "B", 15),
                        new ChartDataNode("4", "节点 4", "B", 25)
                ),
                List.of(
                        new ChartDataLink("1", "2", 5),
                        new ChartDataLink("2", "3", 8),
                        new ChartDataLink("3", "4", 6),
                        new ChartDataLink("1", "4", 3)
                )
        );
    }

    private ChartDataSeries buildTrendSeries() {
        return new ChartDataSeries(
                "demo-trend",
                List.of("一月", "二月", "三月", "四月", "五月", "六月"),
                List.of(120, 200, 150, 80, 70, 110),
                null,
                null
        );
    }
}
