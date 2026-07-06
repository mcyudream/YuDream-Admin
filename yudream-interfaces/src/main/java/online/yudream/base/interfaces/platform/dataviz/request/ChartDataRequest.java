package online.yudream.base.interfaces.platform.dataviz.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;

@Data
public class ChartDataRequest {

    private Long definitionId;

    @NotBlank(message = "图表类型不能为空")
    private String chartType;

    @NotNull(message = "数据集查询条件不能为空")
    private ChartDatasetQuery datasetQuery;
}
