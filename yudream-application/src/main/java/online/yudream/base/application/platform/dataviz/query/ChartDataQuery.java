package online.yudream.base.application.platform.dataviz.query;

import lombok.Data;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;

@Data
public class ChartDataQuery {

    private Long definitionId;
    private ChartType chartType;
    private ChartDatasetQuery datasetQuery;
}
