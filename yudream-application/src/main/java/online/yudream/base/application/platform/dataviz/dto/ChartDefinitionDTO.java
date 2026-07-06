package online.yudream.base.application.platform.dataviz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import online.yudream.base.domain.platform.dataviz.valobj.ChartLayoutConfig;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartDefinitionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
