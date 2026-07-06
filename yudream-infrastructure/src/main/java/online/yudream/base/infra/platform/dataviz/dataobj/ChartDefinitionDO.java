package online.yudream.base.infra.platform.dataviz.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import online.yudream.base.domain.platform.dataviz.valobj.ChartLayoutConfig;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "platformDatavizChartDefinition")
public class ChartDefinitionDO extends BaseDO {

    @Indexed(unique = true)
    private String code;

    private String name;

    private ChartType chartType;

    private String dataSource;

    private ChartDatasetQuery queryConfig;

    private ChartLayoutConfig layoutConfig;

    private boolean enabled;
}
