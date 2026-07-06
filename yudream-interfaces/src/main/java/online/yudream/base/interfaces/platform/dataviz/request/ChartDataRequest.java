package online.yudream.base.interfaces.platform.dataviz.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;

@Data
public class ChartDataRequest {

    private Long definitionId;

    @NotNull(message = "图表类型不能为空")
    private ChartType chartType;

    @NotNull(message = "数据集查询条件不能为空")
    private ChartDatasetQuery datasetQuery;
}
