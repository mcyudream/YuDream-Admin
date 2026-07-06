package online.yudream.base.interfaces.platform.dataviz.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import online.yudream.base.domain.platform.dataviz.valobj.ChartLayoutConfig;

import java.time.LocalDateTime;

@Data
@Builder
public class ChartDefinitionRes {

    private Long id;
    private String code;
    private String name;
    private ChartType chartType;
    private String dataSource;
    private ChartDatasetQuery queryConfig;
    private ChartLayoutConfig layoutConfig;
    private boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
