package online.yudream.base.interfaces.platform.dataviz.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import online.yudream.base.domain.platform.dataviz.valobj.ChartLayoutConfig;

@Data
public class ChartDefinitionUpdateRequest {

    @NotBlank(message = "图表名称不能为空")
    private String name;

    @NotBlank(message = "图表类型不能为空")
    private String chartType;

    private String dataSource;

    private ChartDatasetQuery queryConfig;

    private ChartLayoutConfig layoutConfig;
}
