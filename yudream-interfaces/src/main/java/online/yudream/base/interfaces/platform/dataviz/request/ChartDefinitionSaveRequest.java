package online.yudream.base.interfaces.platform.dataviz.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import online.yudream.base.domain.platform.dataviz.valobj.ChartLayoutConfig;

@Data
public class ChartDefinitionSaveRequest {

    @NotBlank(message = "图表编码不能为空")
    private String code;

    @NotBlank(message = "图表名称不能为空")
    private String name;

    @NotNull(message = "图表类型不能为空")
    private ChartType chartType;

    private String dataSource;

    private ChartDatasetQuery queryConfig;

    private ChartLayoutConfig layoutConfig;
}
