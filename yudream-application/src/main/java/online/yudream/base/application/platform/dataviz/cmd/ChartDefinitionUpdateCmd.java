package online.yudream.base.application.platform.dataviz.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import online.yudream.base.domain.platform.dataviz.valobj.ChartLayoutConfig;

import java.io.Serial;
import java.io.Serializable;

@Data
public class ChartDefinitionUpdateCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private ChartType chartType;
    private String dataSource;
    private ChartDatasetQuery queryConfig;
    private ChartLayoutConfig layoutConfig;
}
